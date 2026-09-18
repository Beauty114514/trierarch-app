package app.trierarch.input

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Owns Android IME-panel visibility independently from how a display protocol
 * forwards the resulting input. Callers supply the focused display View's
 * show operation so this class never needs to know about X11 or Wayland.
 */
class KeyboardPanelController(
    private val hostView: View,
) {
    private var imeVisible = false
    private var manuallySuppressed = false

    fun updateInsets(insets: WindowInsetsCompat) {
        imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
    }

    /** Allows ordinary display taps to summon the IME unless Trierarch hid it. */
    fun requestShowFromTouch(showKeyboard: () -> Unit) {
        if (!manuallySuppressed) showKeyboard()
    }

    /** Explicit menu requests always override a previous manual hide. */
    fun show(showKeyboard: () -> Unit) {
        manuallySuppressed = false
        showKeyboard()
    }

    /** Future menu actions can toggle the panel using its actual Insets state. */
    fun toggle(showKeyboard: () -> Unit) {
        if (imeVisible) hide() else show(showKeyboard)
    }

    /** Prevents the next ordinary display tap from immediately reopening the panel. */
    fun hide() {
        manuallySuppressed = true
        ViewCompat.getWindowInsetsController(hostView)?.hide(WindowInsetsCompat.Type.ime())
    }
}
