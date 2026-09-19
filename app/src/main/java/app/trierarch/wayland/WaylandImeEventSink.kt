package app.trierarch.wayland

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import app.trierarch.input.AndroidImeEvent
import app.trierarch.input.AndroidImeEventSink
import app.trierarch.input.InputMode
import app.trierarch.input.InputModeController
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Delivers Android IME text and base editing keys to the guest bridge in order. */
class WaylandImeEventSink(
    socket: File,
    private val inputMode: InputModeController,
) : AndroidImeEventSink, AutoCloseable {
    private val socketPath = socket.absolutePath
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var composingText = ""
    private var batchEditDepth = 0

    override fun send(event: AndroidImeEvent) {
        val mode = inputMode.current
        executor.execute {
            if (mode == InputMode.KEY) {
                if (event is AndroidImeEvent.KeyEvent) sendRawKeyEvent(event)
                return@execute
            }
            when (event) {
                AndroidImeEvent.BeginBatchEdit -> batchEditDepth++
                AndroidImeEvent.EndBatchEdit -> if (batchEditDepth > 0) batchEditDepth--
                is AndroidImeEvent.CommitText -> replaceComposingText(event.text, keepComposing = false)
                is AndroidImeEvent.SetComposingText -> replaceComposingText(event.text, keepComposing = true)
                AndroidImeEvent.FinishComposingText -> composingText = ""
                is AndroidImeEvent.DeleteSurroundingText -> {
                    composingText = ""
                    sendRepeatedKeysym(XK_BACK_SPACE, event.beforeLength)
                    sendRepeatedKeysym(XK_DELETE, event.afterLength)
                }
                is AndroidImeEvent.DeleteSurroundingTextInCodePoints -> {
                    composingText = ""
                    sendRepeatedKeysym(XK_BACK_SPACE, event.beforeLength)
                    sendRepeatedKeysym(XK_DELETE, event.afterLength)
                }
                is AndroidImeEvent.SetSelection -> {
                    if (batchEditDepth == 0 && event.start == event.end) {
                        when {
                            event.start < 1 -> sendRepeatedKeysym(XK_LEFT, 1)
                            event.start > 1 -> sendRepeatedKeysym(XK_RIGHT, 1)
                        }
                    }
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

    /** Mirrors X11's preedit replacement without exposing stale Android state. */
    private fun replaceComposingText(replacement: String, keepComposing: Boolean) {
        when {
            replacement.startsWith(composingText) -> sendCommittedText(replacement.drop(composingText.length))
            composingText.startsWith(replacement) -> {
                sendRepeatedKeysym(XK_BACK_SPACE, composingText.codePointCount(0, composingText.length) - replacement.codePointCount(0, replacement.length))
            }
            else -> {
                sendRepeatedKeysym(XK_BACK_SPACE, composingText.codePointCount(0, composingText.length))
                sendCommittedText(replacement)
            }
        }
        composingText = if (keepComposing) replacement else ""
    }

    /** Split control characters from final UTF-8 text exactly as the X11 path does. */
    private fun sendCommittedText(text: String) {
        val committed = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            when (val character = text[index]) {
                '\n', '\r', '\u2028', '\u2029' -> {
                    sendTextCommit(committed)
                    committed.setLength(0)
                    sendRepeatedKeysym(XK_RETURN, 1)
                    if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                '\t' -> {
                    sendTextCommit(committed)
                    committed.setLength(0)
                    sendRepeatedKeysym(XK_TAB, 1)
                }
                '\b' -> {
                    sendTextCommit(committed)
                    committed.setLength(0)
                    sendRepeatedKeysym(XK_BACK_SPACE, 1)
                }
                '\u007f' -> {
                    sendTextCommit(committed)
                    committed.setLength(0)
                    sendRepeatedKeysym(XK_DELETE, 1)
                }
                '\u001b' -> {
                    sendTextCommit(committed)
                    committed.setLength(0)
                    sendRepeatedKeysym(XK_ESCAPE, 1)
                }
                else -> if (character >= ' ') committed.append(character)
            }
            index++
        }
        sendTextCommit(committed)
    }

    private fun sendTextCommit(text: StringBuilder) {
        if (text.isNotEmpty()) sendTextCommit(text.toString())
    }

    private fun sendTextCommit(text: String) {
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

    private fun sendRawKeyEvent(event: AndroidImeEvent.KeyEvent) {
        val pressed = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return
        }
        WaylandBridge.setKeyboardKey(
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            pressed = pressed,
            timeMillis = (event.eventTime and 0x7fff_ffffL).toInt(),
        )
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
        KeyEvent.KEYCODE_MOVE_HOME -> XK_HOME
        KeyEvent.KEYCODE_MOVE_END -> XK_END
        KeyEvent.KEYCODE_PAGE_UP -> XK_PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> XK_PAGE_DOWN
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
        const val XK_HOME = 0xff50
        const val XK_END = 0xff57
        const val XK_PAGE_UP = 0xff55
        const val XK_PAGE_DOWN = 0xff56
        const val XK_DELETE = 0xffff
    }
}
