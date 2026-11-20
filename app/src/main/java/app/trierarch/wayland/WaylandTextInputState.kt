package app.trierarch.wayland

import java.io.File

/**
 * Text-input focus as reported by the guest IME bridge.
 *
 * The bridge atomically replaces a small `key=value` state file when the
 * guest's text-input focus changes. Reading that file keeps the Android side
 * free of a second socket and of the bridge's message framing. A missing or
 * unreadable file means no text input is focused.
 */
data class TextInputFocus(
    val active: Boolean,
    val hint: Int,
    val purpose: Int,
    val sequence: Int,
) {
    companion object {
        val INACTIVE = TextInputFocus(active = false, hint = 0, purpose = 0, sequence = 0)

        /** Parses the bridge's state file. Unknown or malformed lines are ignored. */
        fun parse(lines: List<String>): TextInputFocus {
            var active = false
            var hint = 0
            var purpose = 0
            var sequence = 0
            for (line in lines) {
                val separator = line.indexOf('=')
                if (separator <= 0) continue
                val value = line.substring(separator + 1).toIntOrNull() ?: continue
                when (line.substring(0, separator)) {
                    "textInput" -> active = value != 0
                    "hint" -> hint = value
                    "purpose" -> purpose = value
                    "seq" -> sequence = value
                }
            }
            return TextInputFocus(active, hint, purpose, sequence)
        }
    }
}

/** Reads the guest-reported text-input state from the shared IME directory. */
object WaylandTextInputState {
    fun read(file: File): TextInputFocus = try {
        if (file.isFile) TextInputFocus.parse(file.readLines()) else TextInputFocus.INACTIVE
    } catch (_: Exception) {
        TextInputFocus.INACTIVE
    }

    /**
     * Clears a state left behind by an earlier session before the bridge starts.
     * The bridge overwrites the file as soon as the guest focuses a text input.
     */
    fun reset(file: File) {
        try {
            file.writeText("textInput=0\nhint=0\npurpose=0\nseq=0\n")
        } catch (_: Exception) {
            // Best effort: a stale active state only means one unrequested
            // keyboard show, and the next guest focus event replaces the file.
        }
    }
}
