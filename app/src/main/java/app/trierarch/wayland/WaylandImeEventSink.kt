package app.trierarch.wayland

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import app.trierarch.input.AndroidImeEvent
import app.trierarch.input.AndroidImeEventSink
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Delivers Android IME text and base editing keys to the guest bridge in order. */
class WaylandImeEventSink(socket: File) : AndroidImeEventSink, AutoCloseable {
    private val socketPath = socket.absolutePath
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun send(event: AndroidImeEvent) {
        executor.execute {
            when (event) {
                is AndroidImeEvent.CommitText -> if (event.text.isNotEmpty()) sendCommit(event.text)
                is AndroidImeEvent.DeleteSurroundingText -> {
                    sendRepeatedKeysym(XK_BACK_SPACE, event.beforeLength)
                    sendRepeatedKeysym(XK_DELETE, event.afterLength)
                }
                is AndroidImeEvent.DeleteSurroundingTextInCodePoints -> {
                    sendRepeatedKeysym(XK_BACK_SPACE, event.beforeLength)
                    sendRepeatedKeysym(XK_DELETE, event.afterLength)
                }
                is AndroidImeEvent.KeyEvent -> sendKeyEvent(event)
                is AndroidImeEvent.EditorAction -> sendEditorAction(event.actionCode)
                else -> Unit
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun sendCommit(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        sendFrame(bytes.size, bytes)
    }

    private fun sendKeyEvent(event: AndroidImeEvent.KeyEvent) {
        val keysym = keysymFor(event.keyCode) ?: return
        val state = when (event.action) {
            KeyEvent.ACTION_DOWN -> KEY_STATE_PRESSED
            KeyEvent.ACTION_UP -> KEY_STATE_RELEASED
            else -> return
        }
        sendKeysym(keysym, state)
    }

    private fun sendEditorAction(actionCode: Int) {
        when (actionCode) {
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NONE,
            EditorInfo.IME_ACTION_UNSPECIFIED,
            -> sendRepeatedKeysym(XK_RETURN, 1)
        }
    }

    private fun sendRepeatedKeysym(keysym: Int, count: Int) {
        repeat(count.coerceIn(0, MAX_REPEATED_KEYS)) {
            sendKeysym(keysym, KEY_STATE_PRESSED)
            sendKeysym(keysym, KEY_STATE_RELEASED)
        }
    }

    private fun sendKeysym(keysym: Int, state: Int) {
        val payload = byteArrayOf(
            CONTROL_KEYSYM.toByte(),
            state.toByte(),
            (keysym ushr 24).toByte(),
            (keysym ushr 16).toByte(),
            (keysym ushr 8).toByte(),
            keysym.toByte(),
        )
        sendFrame(CONTROL_FRAME_BIT or payload.size, payload)
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

    private fun keysymFor(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DEL -> XK_BACK_SPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> XK_DELETE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> XK_RETURN
        KeyEvent.KEYCODE_TAB -> XK_TAB
        KeyEvent.KEYCODE_ESCAPE -> XK_ESCAPE
        KeyEvent.KEYCODE_DPAD_UP -> XK_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> XK_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> XK_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> XK_RIGHT
        else -> null
    }

    private companion object {
        const val TAG = "TrierarchWaylandIme"
        const val CONTROL_FRAME_BIT = Int.MIN_VALUE
        const val CONTROL_KEYSYM = 1
        const val KEY_STATE_RELEASED = 0
        const val KEY_STATE_PRESSED = 1
        const val MAX_REPEATED_KEYS = 256

        const val XK_BACK_SPACE = 0xff08
        const val XK_TAB = 0xff09
        const val XK_RETURN = 0xff0d
        const val XK_ESCAPE = 0xff1b
        const val XK_LEFT = 0xff51
        const val XK_UP = 0xff52
        const val XK_RIGHT = 0xff53
        const val XK_DOWN = 0xff54
        const val XK_DELETE = 0xffff
    }
}
