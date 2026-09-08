package app.trierarch.input

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Common Android-IME endpoint for desktop display views.
 *
 * This deliberately stops at [AndroidImeEventSink]: it does not turn text into
 * X11 keysyms, wl_keyboard events, or Wayland input-method requests. Keeping
 * that policy outside the Android endpoint lets both desktop protocols share
 * Android composing semantics while their protocol backends evolve separately.
 */
class AndroidImeController @JvmOverloads constructor(
    private val target: View,
    private val sink: AndroidImeEventSink = LoggingAndroidImeEventSink,
) {
    fun createInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.actionLabel = "↵"
        return Connection(target, sink)
    }

    fun showKeyboard() {
        target.post {
            target.requestFocus()
            val manager = target.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private class Connection(
        private val targetView: View,
        private val sink: AndroidImeEventSink,
    ) : BaseInputConnection(targetView, false) {
        private val inputMethodManager = targetView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        private var batchEditDepth = 0
        private var cursorPosition = 1

        /*
         * The guest editor is opaque to Android. Returning no Editable while
         * offering a stable one-character context mirrors Termux:X11 and
         * prevents an IME from editing a stale local buffer instead of sending
         * operations to the desktop target.
         */
        override fun getEditable(): Editable? = null

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence = " "

        override fun getTextAfterCursor(length: Int, flags: Int): CharSequence = " "

        override fun beginBatchEdit(): Boolean {
            batchEditDepth++
            sink.send(AndroidImeEvent.BeginBatchEdit)
            return true
        }

        override fun endBatchEdit(): Boolean {
            if (batchEditDepth > 0) batchEditDepth--
            sink.send(AndroidImeEvent.EndBatchEdit)
            if (batchEditDepth == 0) reportSelection()
            return true
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            sink.send(AndroidImeEvent.CommitText(text.toString(), newCursorPosition))
            cursorPosition = if (newCursorPosition > 0) {
                (cursorPosition + text.length + newCursorPosition - 1).coerceAtLeast(1)
            } else {
                1
            }
            if (batchEditDepth == 0) reportSelection()
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            sink.send(AndroidImeEvent.SetComposingText(text.toString(), newCursorPosition))
            return true
        }

        override fun finishComposingText(): Boolean {
            sink.send(AndroidImeEvent.FinishComposingText)
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            sink.send(AndroidImeEvent.DeleteSurroundingText(beforeLength, afterLength))
            cursorPosition = (cursorPosition - beforeLength).coerceAtLeast(1)
            if (batchEditDepth == 0) reportSelection()
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            sink.send(AndroidImeEvent.DeleteSurroundingTextInCodePoints(beforeLength, afterLength))
            cursorPosition = (cursorPosition - beforeLength).coerceAtLeast(1)
            if (batchEditDepth == 0) reportSelection()
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            sink.send(AndroidImeEvent.SetSelection(start, end))
            if (start == end) cursorPosition = start.coerceAtLeast(1)
            if (batchEditDepth == 0) reportSelection()
            return true
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            sink.send(AndroidImeEvent.SetComposingRegion(start, end))
            return true
        }

        override fun sendKeyEvent(event: AndroidKeyEvent): Boolean {
            sink.send(AndroidImeEvent.KeyEvent(event.action, event.keyCode))
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            sink.send(AndroidImeEvent.EditorAction(actionCode))
            return true
        }

        private fun reportSelection() {
            inputMethodManager.updateSelection(targetView, cursorPosition, cursorPosition, -1, -1)
        }
    }
}

/** Semantic events received from an Android virtual keyboard. */
sealed interface AndroidImeEvent {
    data object BeginBatchEdit : AndroidImeEvent
    data object EndBatchEdit : AndroidImeEvent
    data class CommitText(val text: String, val newCursorPosition: Int) : AndroidImeEvent
    data class SetComposingText(val text: String, val newCursorPosition: Int) : AndroidImeEvent
    data object FinishComposingText : AndroidImeEvent
    data class DeleteSurroundingText(val beforeLength: Int, val afterLength: Int) : AndroidImeEvent
    data class DeleteSurroundingTextInCodePoints(val beforeLength: Int, val afterLength: Int) : AndroidImeEvent
    data class SetSelection(val start: Int, val end: Int) : AndroidImeEvent
    data class SetComposingRegion(val start: Int, val end: Int) : AndroidImeEvent
    data class KeyEvent(val action: Int, val keyCode: Int) : AndroidImeEvent
    data class EditorAction(val actionCode: Int) : AndroidImeEvent
}

fun interface AndroidImeEventSink {
    fun send(event: AndroidImeEvent)
}

/** Temporary stage-two telemetry. Text contents are intentionally not logged. */
private object LoggingAndroidImeEventSink : AndroidImeEventSink {
    private const val TAG = "TrierarchIme"

    override fun send(event: AndroidImeEvent) {
        val summary = when (event) {
            AndroidImeEvent.BeginBatchEdit -> "beginBatchEdit"
            AndroidImeEvent.EndBatchEdit -> "endBatchEdit"
            is AndroidImeEvent.CommitText -> "commitText chars=${event.text.length} cursor=${event.newCursorPosition}"
            is AndroidImeEvent.SetComposingText -> "setComposingText chars=${event.text.length} cursor=${event.newCursorPosition}"
            AndroidImeEvent.FinishComposingText -> "finishComposingText"
            is AndroidImeEvent.DeleteSurroundingText -> "deleteSurroundingText before=${event.beforeLength} after=${event.afterLength}"
            is AndroidImeEvent.DeleteSurroundingTextInCodePoints -> "deleteSurroundingTextInCodePoints before=${event.beforeLength} after=${event.afterLength}"
            is AndroidImeEvent.SetSelection -> "setSelection start=${event.start} end=${event.end}"
            is AndroidImeEvent.SetComposingRegion -> "setComposingRegion start=${event.start} end=${event.end}"
            is AndroidImeEvent.KeyEvent -> "keyEvent action=${event.action} keyCode=${event.keyCode}"
            is AndroidImeEvent.EditorAction -> "editorAction code=${event.actionCode}"
        }
        Log.i(TAG, summary)
    }
}
