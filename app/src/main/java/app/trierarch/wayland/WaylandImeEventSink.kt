package app.trierarch.wayland

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import app.trierarch.input.AndroidImeEvent
import app.trierarch.input.AndroidImeEventSink
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Delivers committed Android-IME UTF-8 text to the guest bridge in order. */
class WaylandImeEventSink(socket: File) : AndroidImeEventSink, AutoCloseable {
    private val socketPath = socket.absolutePath
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun send(event: AndroidImeEvent) {
        if (event !is AndroidImeEvent.CommitText || event.text.isEmpty()) return
        val text = event.text
        executor.execute { sendCommit(text) }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun sendCommit(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        LocalSocket().use { socket ->
            try {
                socket.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
                DataOutputStream(socket.outputStream).use { output ->
                    output.writeInt(bytes.size)
                    output.write(bytes)
                    output.flush()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Unable to deliver ${bytes.size} IME UTF-8 bytes to Wayland bridge", error)
            }
        }
    }

    private companion object {
        const val TAG = "TrierarchWaylandIme"
    }
}
