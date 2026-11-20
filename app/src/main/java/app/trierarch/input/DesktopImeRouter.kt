package app.trierarch.input

import android.view.KeyEvent

/**
 * Protocol-neutral sink for key-shaped input.
 *
 * Keys stay Android key codes here, the same values an input method reports;
 * each display protocol maps them to its own wire format. [sendKey] returns
 * whether the target accepted the event, so a rejected press is not released
 * later on a different surface.
 */
fun interface DesktopKeySink {
    fun sendKey(keyCode: Int, scanCode: Int, pressed: Boolean): Boolean
}

/** Protocol-neutral sink for final Unicode text. */
fun interface DesktopTextSink {
    fun sendText(text: String)
}

/**
 * Routes Android input-method events to a desktop display host.
 *
 * There is one rule for every desktop protocol: final Unicode **text** goes to
 * [textSink] because only a text channel can carry CJK, while every key-shaped
 * event goes to [keySink] as a real key press, exactly what a physical keyboard
 * would produce. Editing keys are key events rather than protocol-specific text
 * operations because guests disagree about those operations: wlroots'
 * input-method v2 has no keysym request, its delete request counts bytes, and
 * terminals such as kitty ignore it entirely.
 *
 * Committed text is split around control characters. Printable runs go to
 * [textSink]; newline, tab, backspace, delete and escape become real key
 * presses. A committed single character is retyped through [keySink] while a
 * shortcut modifier (Ctrl/Alt/Super) is held, which is how a keyboard-style
 * chord such as Ctrl+C reaches guests even when the input method delivers the
 * letter as text.
 *
 * The router is single-threaded. Callers serialize events and call [reset] when
 * the focused surface changes, so a held modifier cannot leak into the next one.
 */
class DesktopImeRouter(
    private val keySink: DesktopKeySink,
    private val textSink: DesktopTextSink,
) : AndroidImeEventSink {
    private var composingText = ""
    private var batchEditDepth = 0

    /** Keys held in the guest: Android key code to the scan code used for it. */
    private val pressedKeys = linkedMapOf<Int, Int>()

    /** Modifiers pressed only for one key: key code to the modifiers pressed. */
    private val synthesizedModifiers = mutableMapOf<Int, List<Int>>()

    override fun send(event: AndroidImeEvent) {
        when (event) {
            AndroidImeEvent.BeginBatchEdit -> batchEditDepth++
            AndroidImeEvent.EndBatchEdit -> if (batchEditDepth > 0) batchEditDepth--
            is AndroidImeEvent.CommitText -> replaceComposingText(event.text, keepComposing = false)
            is AndroidImeEvent.SetComposingText -> replaceComposingText(event.text, keepComposing = true)
            AndroidImeEvent.FinishComposingText -> composingText = ""
            is AndroidImeEvent.DeleteSurroundingText ->
                deleteSurroundingText(event.beforeLength, event.afterLength)
            is AndroidImeEvent.DeleteSurroundingTextInCodePoints ->
                deleteSurroundingText(event.beforeLength, event.afterLength)
            is AndroidImeEvent.SetSelection -> moveSelection(event.start, event.end)
            is AndroidImeEvent.SetComposingRegion -> Unit
            is AndroidImeEvent.KeyEvent -> dispatchKey(event)
            is AndroidImeEvent.EditorAction -> pressKey(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** Drops preedit state and releases anything the input method still holds. */
    fun reset() {
        composingText = ""
        batchEditDepth = 0
        val held = pressedKeys.entries.toList().asReversed()
        pressedKeys.clear()
        held.forEach { (keyCode, scanCode) -> keySink.sendKey(keyCode, scanCode, false) }
        synthesizedModifiers.values.forEach { modifiers ->
            modifiers.asReversed().forEach { keySink.sendKey(it, 0, false) }
        }
        synthesizedModifiers.clear()
    }

    /**
     * Mirrors the preedit replacement model of X11 hosts: a growing compose
     * commits only the suffix, a shrinking one backs over the difference, and an
     * unrelated replacement backs over the whole previous compose first.
     */
    private fun replaceComposingText(replacement: String, keepComposing: Boolean) {
        when {
            replacement.startsWith(composingText) ->
                commitText(replacement.substring(composingText.length))
            composingText.startsWith(replacement) ->
                repeatKey(KeyEvent.KEYCODE_DEL, codePointCount(composingText) - codePointCount(replacement))
            else -> {
                repeatKey(KeyEvent.KEYCODE_DEL, codePointCount(composingText))
                commitText(replacement)
            }
        }
        composingText = if (keepComposing) replacement else ""
    }

    private fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        composingText = ""
        repeatKey(KeyEvent.KEYCODE_DEL, beforeLength)
        repeatKey(KeyEvent.KEYCODE_FORWARD_DEL, afterLength)
    }

    /**
     * The input method sees a one-character context; a collapsed selection away
     * from that cursor is expressed as one arrow key.
     */
    private fun moveSelection(start: Int, end: Int) {
        if (batchEditDepth != 0 || start != end) return
        when {
            start < 1 -> pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
            start > 1 -> pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        }
    }

    private fun commitText(text: String) {
        if (text.isEmpty()) return
        if (hasShortcutModifier() && pressTextAsKeys(text)) return
        sendPrintableText(text)
    }

    /** Splits final text into printable runs and real key presses. */
    private fun sendPrintableText(text: String) {
        val printable = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            when (val character = text[index]) {
                '\n', '\r', '\u2028', '\u2029' -> {
                    flushText(printable)
                    pressKey(KeyEvent.KEYCODE_ENTER)
                    if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                '\t' -> {
                    flushText(printable)
                    pressKey(KeyEvent.KEYCODE_TAB)
                }
                '\b' -> {
                    flushText(printable)
                    pressKey(KeyEvent.KEYCODE_DEL)
                }
                '\u007f' -> {
                    flushText(printable)
                    pressKey(KeyEvent.KEYCODE_FORWARD_DEL)
                }
                '\u001b' -> {
                    flushText(printable)
                    pressKey(KeyEvent.KEYCODE_ESCAPE)
                }
                else -> if (character >= ' ') printable.append(character)
            }
            index++
        }
        flushText(printable)
    }

    private fun flushText(text: StringBuilder) {
        if (text.isEmpty()) return
        textSink.sendText(text.toString())
        text.setLength(0)
    }

    private fun dispatchKey(event: AndroidImeEvent.KeyEvent) {
        val pressed = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return
        }
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val scanCode = if (pressed) {
            event.scanCode
        } else {
            pressedKeys.remove(keyCode) ?: event.scanCode
        }
        if (pressed) {
            val firstPress = !pressedKeys.containsKey(keyCode)
            val synthesized = if (firstPress) pressSynthesizedModifiers(keyCode, event.metaState) else emptyList()
            if (synthesized.isNotEmpty()) synthesizedModifiers[keyCode] = synthesized
            if (keySink.sendKey(keyCode, scanCode, true)) pressedKeys[keyCode] = scanCode
        } else {
            keySink.sendKey(keyCode, scanCode, false)
            releaseSynthesizedModifiers(keyCode)
        }
    }

    /**
     * Some keyboards encode a chord only in the event's meta state instead of
     * sending modifier key events. Press those modifiers around the key so the
     * guest sees the same modifier state as a hardware keyboard would produce.
     */
    private fun pressSynthesizedModifiers(keyCode: Int, metaState: Int): List<Int> {
        if (keyCode in MODIFIER_KEY_CODES) return emptyList()
        val missing = modifierKeyCodes(metaState).filterNot(::isModifierHeld)
        missing.forEach { keySink.sendKey(it, 0, true) }
        return missing
    }

    private fun releaseSynthesizedModifiers(keyCode: Int) {
        val modifiers = synthesizedModifiers.remove(keyCode) ?: return
        modifiers.asReversed().forEach { keySink.sendKey(it, 0, false) }
    }

    private fun modifierKeyCodes(metaState: Int): List<Int> = buildList {
        if (metaState and KeyEvent.META_SHIFT_ON != 0) add(KeyEvent.KEYCODE_SHIFT_LEFT)
        if (metaState and KeyEvent.META_CTRL_ON != 0) add(KeyEvent.KEYCODE_CTRL_LEFT)
        if (metaState and KeyEvent.META_ALT_ON != 0) add(KeyEvent.KEYCODE_ALT_LEFT)
        if (metaState and KeyEvent.META_META_ON != 0) add(KeyEvent.KEYCODE_META_LEFT)
    }

    private fun isModifierHeld(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT ->
            pressedKeys.containsKey(KeyEvent.KEYCODE_SHIFT_LEFT) ||
                pressedKeys.containsKey(KeyEvent.KEYCODE_SHIFT_RIGHT)
        KeyEvent.KEYCODE_CTRL_LEFT ->
            pressedKeys.containsKey(KeyEvent.KEYCODE_CTRL_LEFT) ||
                pressedKeys.containsKey(KeyEvent.KEYCODE_CTRL_RIGHT)
        KeyEvent.KEYCODE_ALT_LEFT ->
            pressedKeys.containsKey(KeyEvent.KEYCODE_ALT_LEFT) ||
                pressedKeys.containsKey(KeyEvent.KEYCODE_ALT_RIGHT)
        KeyEvent.KEYCODE_META_LEFT ->
            pressedKeys.containsKey(KeyEvent.KEYCODE_META_LEFT) ||
                pressedKeys.containsKey(KeyEvent.KEYCODE_META_RIGHT)
        else -> pressedKeys.containsKey(keyCode)
    }

    private fun hasShortcutModifier(): Boolean =
        isModifierHeld(KeyEvent.KEYCODE_CTRL_LEFT) ||
            isModifierHeld(KeyEvent.KEYCODE_ALT_LEFT) ||
            isModifierHeld(KeyEvent.KEYCODE_META_LEFT)

    /**
     * Retypes one committed character while a shortcut modifier is held. Letters
     * ignore case because auto-capitalisation must not turn Ctrl+C into
     * Ctrl+Shift+C; an explicitly held Shift still applies in the guest.
     */
    private fun pressTextAsKeys(text: String): Boolean {
        if (text.length != 1) return false
        val stroke = asciiStroke(text[0]) ?: return false
        withShift(stroke.shift) { pressKey(stroke.keyCode) }
        return true
    }

    private inline fun withShift(shift: Boolean, action: () -> Unit) {
        if (!shift || isModifierHeld(KeyEvent.KEYCODE_SHIFT_LEFT)) {
            action()
            return
        }
        keySink.sendKey(KeyEvent.KEYCODE_SHIFT_LEFT, 0, true)
        try {
            action()
        } finally {
            keySink.sendKey(KeyEvent.KEYCODE_SHIFT_LEFT, 0, false)
        }
    }

    private fun pressKey(keyCode: Int) {
        keySink.sendKey(keyCode, 0, true)
        keySink.sendKey(keyCode, 0, false)
    }

    private fun repeatKey(keyCode: Int, count: Int) {
        repeat(count.coerceIn(0, MAX_REPEATED_KEYS)) { pressKey(keyCode) }
    }

    private fun codePointCount(text: String): Int = text.codePointCount(0, text.length)

    private data class KeyStroke(val keyCode: Int, val shift: Boolean)

    /** US-layout mapping, matching the key map the Wayland host sends. */
    private fun asciiStroke(character: Char): KeyStroke? {
        when (character) {
            in 'a'..'z', in 'A'..'Z' -> {
                val lower = character.lowercaseChar()
                // Android key codes for letters and digits are contiguous ranges.
                return KeyStroke(KeyEvent.KEYCODE_A + (lower - 'a'), shift = false)
            }
            in '0'..'9' -> return KeyStroke(KeyEvent.KEYCODE_0 + (character - '0'), shift = false)
        }
        return PLAIN_STROKES[character] ?: SHIFTED_STROKES[character]
    }

    private companion object {
        const val MAX_REPEATED_KEYS = 256

        val MODIFIER_KEY_CODES = setOf(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT,
        )

        val PLAIN_STROKES: Map<Char, KeyStroke> = mapOf(
            ' ' to KeyStroke(KeyEvent.KEYCODE_SPACE, false),
            '`' to KeyStroke(KeyEvent.KEYCODE_GRAVE, false),
            '-' to KeyStroke(KeyEvent.KEYCODE_MINUS, false),
            '=' to KeyStroke(KeyEvent.KEYCODE_EQUALS, false),
            '[' to KeyStroke(KeyEvent.KEYCODE_LEFT_BRACKET, false),
            ']' to KeyStroke(KeyEvent.KEYCODE_RIGHT_BRACKET, false),
            '\\' to KeyStroke(KeyEvent.KEYCODE_BACKSLASH, false),
            ';' to KeyStroke(KeyEvent.KEYCODE_SEMICOLON, false),
            '\'' to KeyStroke(KeyEvent.KEYCODE_APOSTROPHE, false),
            ',' to KeyStroke(KeyEvent.KEYCODE_COMMA, false),
            '.' to KeyStroke(KeyEvent.KEYCODE_PERIOD, false),
            '/' to KeyStroke(KeyEvent.KEYCODE_SLASH, false),
        )

        val SHIFTED_STROKES: Map<Char, KeyStroke> = mapOf(
            '~' to KeyStroke(KeyEvent.KEYCODE_GRAVE, true),
            '!' to KeyStroke(KeyEvent.KEYCODE_1, true),
            '@' to KeyStroke(KeyEvent.KEYCODE_2, true),
            '#' to KeyStroke(KeyEvent.KEYCODE_3, true),
            '$' to KeyStroke(KeyEvent.KEYCODE_4, true),
            '%' to KeyStroke(KeyEvent.KEYCODE_5, true),
            '^' to KeyStroke(KeyEvent.KEYCODE_6, true),
            '&' to KeyStroke(KeyEvent.KEYCODE_7, true),
            '*' to KeyStroke(KeyEvent.KEYCODE_8, true),
            '(' to KeyStroke(KeyEvent.KEYCODE_9, true),
            ')' to KeyStroke(KeyEvent.KEYCODE_0, true),
            '_' to KeyStroke(KeyEvent.KEYCODE_MINUS, true),
            '+' to KeyStroke(KeyEvent.KEYCODE_EQUALS, true),
            '{' to KeyStroke(KeyEvent.KEYCODE_LEFT_BRACKET, true),
            '}' to KeyStroke(KeyEvent.KEYCODE_RIGHT_BRACKET, true),
            '|' to KeyStroke(KeyEvent.KEYCODE_BACKSLASH, true),
            ':' to KeyStroke(KeyEvent.KEYCODE_SEMICOLON, true),
            '"' to KeyStroke(KeyEvent.KEYCODE_APOSTROPHE, true),
            '<' to KeyStroke(KeyEvent.KEYCODE_COMMA, true),
            '>' to KeyStroke(KeyEvent.KEYCODE_PERIOD, true),
            '?' to KeyStroke(KeyEvent.KEYCODE_SLASH, true),
        )
    }
}
