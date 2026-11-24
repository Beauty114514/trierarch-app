package app.trierarch.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTPS source for the generated manifest; the URL is fixed in the app. */
internal object UpdateSource {
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/Beauty114514/trierarch-app/main/manifest.toml"

    fun fetch(timestamp: Long = System.currentTimeMillis()): String {
        val connection = (
            URL("$MANIFEST_URL?t=$timestamp").openConnection() as HttpURLConnection
            ).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "Trierarch-Android")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("manifest request failed: HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
