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
    /** Toggles the panel from the Android window's current, uncached IME state. */
    fun toggle(showKeyboard: () -> Unit) {
        if (isImeVisibleNow()) hide() else showKeyboard()
    }

    fun hide() {
        ViewCompat.getWindowInsetsController(hostView)?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun isImeVisibleNow(): Boolean =
        ViewCompat.getRootWindowInsets(hostView)
            ?.isVisible(WindowInsetsCompat.Type.ime())
            ?: false
}
