package app.trierarch.feature.rootfs

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Reads the Linux Containers simplestreams index. Trierarch does not host any
 * of these archives; it only filters the upstream catalog by architecture.
 */
class LxcCatalog(
    private val cacheFile: File? = null,
    private val arch: String = "arm64",
    private val fetch: () -> String = { fetchIndex() },
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL,
) {
    fun load(force: Boolean = false): List<LxcImage> {
        val cached = cacheFile?.takeIf { it.isFile }
        val cachedText = cached?.let { runCatching { it.readText() }.getOrNull() }
        val fresh = !force && cached != null && cachedText != null &&
            now() - cached.lastModified() < ttlMillis
        if (fresh) {
            runCatching { parse(cachedText!!, arch) }.getOrNull()?.let { return it }
        }

        val text = runCatching { fetch() }.getOrElse { error ->
            if (cachedText != null) {
                return runCatching { parse(cachedText, arch) }.getOrElse { throw error }
            }
            throw error
        }
        cacheFile?.let { file ->
            file.parentFile?.mkdirs()
            runCatching { file.writeText(text) }
        }
        return parse(text, arch)
    }

    companion object {
        const val INDEX_URL = "https://images.linuxcontainers.org/streams/v1/images.json"
        const val DOWNLOAD_BASE = "https://images.linuxcontainers.org/"
        const val DEFAULT_TTL = 6L * 60 * 60 * 1000

        fun parse(text: String, arch: String = "arm64"): List<LxcImage> {
            val products = JSONObject(text).optJSONObject("products") ?: return emptyList()
            val images = mutableListOf<LxcImage>()
            val keys = products.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val product = products.optJSONObject(key) ?: continue
                if (!product.optString("arch").equals(arch, ignoreCase = true)) continue
                if (product.optString("variant") != "default") continue

                val versions = product.optJSONObject("versions") ?: continue
                val latest = versions.keys().asSequence().maxOrNull() ?: continue
                val items = versions.optJSONObject(latest)?.optJSONObject("items") ?: continue
                val rootfs = items.optJSONObject("root.tar.xz") ?: continue
                val path = rootfs.optString("path").takeIf { it.isNotBlank() } ?: continue
                val sha256 = rootfs.optString("sha256").takeIf { it.isNotBlank() } ?: continue
                val size = rootfs.optLong("size", -1L)
                if (size <= 0) continue

                images += LxcImage(
                    product = key,
                    os = product.optString("os").ifBlank { key.substringBefore(':') },
                    release = product.optString("release"),
                    variant = product.optString("variant"),
                    arch = arch,
                    buildDate = latest,
                    downloadUrl = DOWNLOAD_BASE + path,
                    size = size,
                    sha256 = sha256,
                )
            }
            return images.sortedWith(
                compareByDescending<LxcImage> { it.recommended }
                    .thenBy { it.os.lowercase() }
                    .thenBy { it.release.lowercase() },
            )
        }

        private fun fetchIndex(): String {
            val connection = (URL(INDEX_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Trierarch-Android")
            }
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    error("image index: HTTP $code")
                }
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
