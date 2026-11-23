package app.trierarch.wayland

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Follows the guest clipboard helper's socket and publishes text to the
 * Android clipboard.
 *
 * The helper runs inside the guest and is started with the session, so this
 * reader retries while the socket is missing and reconnects after a session
 * restart. Only plain UTF-8 text frames are understood; the helper enforces
 * the 64 KiB limit.
 */
class WaylandClipboardBridge(
    context: Context,
    private val socket: File,
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val thread = Thread(::run, "TrierarchClipboard").apply { isDaemon = true }

    @Volatile
    private var running = true

    @Volatile
    private var connection: LocalSocket? = null

    private var lastText: String? = null

    fun start() {
        thread.start()
    }

    fun stop() {
        running = false
        connection?.close()
        connection = null
        thread.interrupt()
    }

    private fun run() {
        while (running) {
            try {
                val client = LocalSocket().apply {
                    connect(
                        LocalSocketAddress(socket.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM),
                    )
                }
                connection = client
                client.use { open ->
                    val input = DataInputStream(open.getInputStream())
                    while (running) {
                        val length = input.readInt()
                        if (length <= 0 || length > MAX_BYTES) {
                            Log.w(TAG, "ignoring clipboard frame of $length bytes")
                            break
                        }
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        publish(String(bytes, StandardCharsets.UTF_8))
                    }
                }
            } catch (error: IOException) {
                // The helper is not running yet, or the session is restarting.
            } finally {
                connection = null
            }
            if (running) {
                try {
                    Thread.sleep(RETRY_MILLIS)
                } catch (interrupted: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun publish(text: String) {
        if (text.isEmpty() || text == lastText) return
        lastText = text
        mainHandler.post {
            if (!running) return@post
            runCatching {
                clipboard.setPrimaryClip(ClipData.newPlainText("Linux", text))
            }.onFailure { error ->
                Log.w(TAG, "unable to publish the guest clipboard", error)
            }
        }
    }

    private companion object {
        const val TAG = "TrierarchClipboard"
        const val MAX_BYTES = 64 * 1024
        const val RETRY_MILLIS = 500L
    }
}
