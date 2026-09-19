package app.trierarch.input

/**
 * Chooses how Android-originated keyboard input is interpreted for a graphical
 * session. Protocol hosts consume this semantic state through their own X11 or
 * Wayland adapters; it is not a desktop-profile setting.
 */
enum class InputMode {
    /** Deliver final UTF-8 text from Android input methods. */
    TEXT,

    /** Deliver Android key press and release events to the desktop protocol. */
    KEY,
}

/**
 * Single in-memory authority for the active graphical session's input mode.
 *
 * The default is text because it supports ordinary Android keyboards and
 * Unicode input without requiring a guest input-method daemon.
 */
class InputModeController(
    initialMode: InputMode = InputMode.TEXT,
) {
    var current: InputMode = initialMode
        private set

    fun select(mode: InputMode) {
        current = mode
    }

    fun toggle(): InputMode {
        current = when (current) {
            InputMode.TEXT -> InputMode.KEY
            InputMode.KEY -> InputMode.TEXT
        }
        return current
    }
}
