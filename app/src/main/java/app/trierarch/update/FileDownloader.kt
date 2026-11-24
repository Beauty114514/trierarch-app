package app.trierarch.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a file to a `.part` file, then verifies size and SHA-256. */
internal object FileDownloader {
    fun download(
        url: String,
        destination: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File {
        val partial = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        partial.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Trierarch-Android")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("download failed: HTTP $code")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var downloaded = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
            if (downloaded != expectedSize) {
                throw IOException("size mismatch: $downloaded != $expectedSize")
            }
            if (!sha256Hex(partial).equals(expectedSha256, ignoreCase = true)) {
                throw IOException("SHA-256 mismatch")
            }
            destination.delete()
            if (!partial.renameTo(destination)) {
                throw IOException("cannot move the downloaded file")
            }
            return destination
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }
}
