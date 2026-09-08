package app.trierarch.input

import android.content.Context
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
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
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
        target: View,
        private val sink: AndroidImeEventSink,
    ) : BaseInputConnection(target, true) {
        override fun beginBatchEdit(): Boolean = super.beginBatchEdit().also {
            if (it) sink.send(AndroidImeEvent.BeginBatchEdit)
        }

        override fun endBatchEdit(): Boolean = super.endBatchEdit().also {
            if (it) sink.send(AndroidImeEvent.EndBatchEdit)
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean =
            super.commitText(text, newCursorPosition).also {
                if (it) sink.send(AndroidImeEvent.CommitText(text.toString(), newCursorPosition))
            }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean =
            super.setComposingText(text, newCursorPosition).also {
                if (it) sink.send(AndroidImeEvent.SetComposingText(text.toString(), newCursorPosition))
            }

        override fun finishComposingText(): Boolean = super.finishComposingText().also {
            if (it) sink.send(AndroidImeEvent.FinishComposingText)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
            super.deleteSurroundingText(beforeLength, afterLength).also {
                if (it) sink.send(AndroidImeEvent.DeleteSurroundingText(beforeLength, afterLength))
            }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
            super.deleteSurroundingTextInCodePoints(beforeLength, afterLength).also {
                if (it) sink.send(AndroidImeEvent.DeleteSurroundingTextInCodePoints(beforeLength, afterLength))
            }

        override fun setSelection(start: Int, end: Int): Boolean = super.setSelection(start, end).also {
            if (it) sink.send(AndroidImeEvent.SetSelection(start, end))
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean = super.setComposingRegion(start, end).also {
            if (it) sink.send(AndroidImeEvent.SetComposingRegion(start, end))
        }

        override fun sendKeyEvent(event: AndroidKeyEvent): Boolean = super.sendKeyEvent(event).also {
            if (it) sink.send(AndroidImeEvent.KeyEvent(event.action, event.keyCode))
        }

        override fun performEditorAction(actionCode: Int): Boolean = super.performEditorAction(actionCode).also {
            if (it) sink.send(AndroidImeEvent.EditorAction(actionCode))
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
