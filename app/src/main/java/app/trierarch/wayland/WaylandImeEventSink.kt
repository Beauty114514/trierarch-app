package app.trierarch.wayland

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import android.util.Log
import app.trierarch.input.AndroidImeEvent
import app.trierarch.input.AndroidImeEventSink
import app.trierarch.input.DesktopImeRouter
import app.trierarch.input.TextChannelAvailability
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Delivers routed Android IME events to the guest Wayland session.
 *
 * Printable text is written to the bridge socket as UTF-8 frames; every
 * key-shaped event is injected through the Wayland host seat, exactly like a
 * physical keyboard. Keeping the two paths separate is what lets a guest with a
 * text-input object receive CJK text while a terminal or game that never creates
 * one still receives real keys. When no text input is focused, committed text
 * is retyped as keys instead of being sent to a bridge that would drop it.
 */
class WaylandImeEventSink(
    socket: File,
    textChannelAvailable: TextChannelAvailability,
) : AndroidImeEventSink, AutoCloseable {
    private val socketPath = socket.absolutePath
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val router = DesktopImeRouter(
        keySink = { keyCode, scanCode, pressed ->
            WaylandBridge.setKeyboardKey(
                keyCode = keyCode,
                scanCode = scanCode,
                pressed = pressed,
                timeMillis = SystemClock.uptimeMillis().toWaylandTime(),
            )
        },
        textSink = { text -> sendTextFrame(text) },
        textChannelAvailable = textChannelAvailable,
    )

    override fun send(event: AndroidImeEvent) {
        executor.execute { router.send(event) }
    }

    /** Releases keys the input method may still hold and drops preedit state. */
    fun resetInputState() {
        executor.execute { router.reset() }
    }

    override fun close() {
        // Let the queued release run before the executor stops; a held modifier
        // must not outlive the surface it was pressed on.
        resetInputState()
        executor.shutdown()
    }

    private fun sendTextFrame(text: String) {
        if (text.isEmpty()) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        sendFrame(bytes.size, bytes)
    }

    /** The bridge's legacy frame remains `[u32 byte count][UTF-8 bytes]`. */
    private fun sendFrame(header: Int, payload: ByteArray) {
        LocalSocket().use { socket ->
            try {
                socket.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
                DataOutputStream(socket.outputStream).use { output ->
                    output.writeInt(header)
                    output.write(payload)
                    output.flush()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Unable to deliver Wayland IME frame of ${payload.size} bytes", error)
            }
        }
    }

    private fun Long.toWaylandTime(): Int = (this and 0x7fff_ffffL).toInt()

    private companion object {
        const val TAG = "TrierarchWaylandIme"
    }
}
