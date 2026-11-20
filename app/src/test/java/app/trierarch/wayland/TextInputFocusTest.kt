package app.trierarch.wayland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextInputFocusTest {
    @Test
    fun `parses an active focused state`() {
        val focus = TextInputFocus.parse(
            listOf("textInput=1", "hint=1", "purpose=13", "seq=7"),
        )

        assertEquals(TextInputFocus(active = true, hint = 1, purpose = 13, sequence = 7), focus)
        assertTrue(focus.active)
    }

    @Test
    fun `ignores unknown and malformed lines`() {
        val focus = TextInputFocus.parse(
            listOf("garbage", "textInput=0", "hint=x", "seq=", "purpose=3"),
        )

        assertEquals(TextInputFocus(active = false, hint = 0, purpose = 3, sequence = 0), focus)
    }

    @Test
    fun `missing file reads as inactive`() {
        val file = File.createTempFile("trierarch-state", ".missing").also { it.delete() }

        assertFalse(WaylandTextInputState.read(file).active)
    }

    @Test
    fun `reset writes an inactive state`() {
        val file = File.createTempFile("trierarch-state", ".txt").also { it.delete() }

        WaylandTextInputState.reset(file)

        assertEquals(TextInputFocus.INACTIVE, WaylandTextInputState.read(file))
        file.delete()
    }
}
