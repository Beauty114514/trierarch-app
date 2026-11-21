package app.trierarch.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopImeRouterTest {
    private val keys = mutableListOf<SentKey>()
    private val texts = mutableListOf<String>()
    private var accepted = true

    private val router = DesktopImeRouter(
        keySink = { keyCode, scanCode, pressed ->
            keys += SentKey(keyCode, scanCode, pressed)
            accepted
        },
        textSink = { texts += it },
    )

    @Test
    fun `final text goes to the text sink`() {
        router.send(AndroidImeEvent.CommitText("hello", 1))

        assertEquals(listOf("hello"), texts)
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `control characters in committed text become key presses`() {
        router.send(AndroidImeEvent.CommitText("a\nb\t", 1))

        assertEquals(listOf("a", "b"), texts)
        assertEquals(
            listOf(
                SentKey(KeyEvent.KEYCODE_ENTER, 0, true),
                SentKey(KeyEvent.KEYCODE_ENTER, 0, false),
                SentKey(KeyEvent.KEYCODE_TAB, 0, true),
                SentKey(KeyEvent.KEYCODE_TAB, 0, false),
            ),
            keys,
        )
    }

    @Test
    fun `key events go to the key sink`() {
        router.send(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
        router.send(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))

        assertEquals(
            listOf(
                SentKey(KeyEvent.KEYCODE_ESCAPE, 0, true),
                SentKey(KeyEvent.KEYCODE_ESCAPE, 0, false),
            ),
            keys,
        )
    }

    @Test
    fun `delete surrounding text becomes editing keys`() {
        router.send(AndroidImeEvent.DeleteSurroundingText(2, 1))

        assertEquals(
            listOf(
                keyStroke(KeyEvent.KEYCODE_DEL),
                keyStroke(KeyEvent.KEYCODE_DEL),
                keyStroke(KeyEvent.KEYCODE_FORWARD_DEL),
            ).flatten(),
            keys,
        )
    }

    @Test
    fun `shrinking preedit backs over the difference`() {
        router.send(AndroidImeEvent.SetComposingText("你好", 1))
        router.send(AndroidImeEvent.SetComposingText("你", 1))

        assertEquals(listOf("你好"), texts)
        assertEquals(keyStroke(KeyEvent.KEYCODE_DEL), keys)
    }

    @Test
    fun `unrelated preedit replacement backs over the whole compose`() {
        router.send(AndroidImeEvent.SetComposingText("nihao", 1))
        router.send(AndroidImeEvent.SetComposingText("你好", 1))

        assertEquals(listOf("nihao", "你好"), texts)
        assertEquals(List(5) { keyStroke(KeyEvent.KEYCODE_DEL) }.flatten(), keys)
    }

    @Test
    fun `held control turns a single committed character into a chord`() {
        router.send(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
        router.send(AndroidImeEvent.CommitText("c", 1))
        router.send(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT))

        assertEquals(keyStroke(KeyEvent.KEYCODE_C), keys.subList(1, 3))
        assertTrue(texts.isEmpty())
    }

    @Test
    fun `meta state synthesizes a modifier around the key`() {
        router.send(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, metaState = KeyEvent.META_SHIFT_ON))
        router.send(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, metaState = KeyEvent.META_SHIFT_ON))

        assertEquals(
            listOf(
                SentKey(KeyEvent.KEYCODE_SHIFT_LEFT, 0, true),
                SentKey(KeyEvent.KEYCODE_A, 0, true),
                SentKey(KeyEvent.KEYCODE_A, 0, false),
                SentKey(KeyEvent.KEYCODE_SHIFT_LEFT, 0, false),
            ),
            keys,
        )
    }

    @Test
    fun `editor action sends enter`() {
        router.send(AndroidImeEvent.EditorAction(6))

        assertEquals(keyStroke(KeyEvent.KEYCODE_ENTER), keys)
    }

    @Test
    fun `reset releases held keys`() {
        router.send(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
        router.send(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))

        router.reset()

        assertEquals(
            listOf(
                SentKey(KeyEvent.KEYCODE_CTRL_LEFT, 0, true),
                SentKey(KeyEvent.KEYCODE_A, 0, true),
                SentKey(KeyEvent.KEYCODE_A, 0, false),
                SentKey(KeyEvent.KEYCODE_CTRL_LEFT, 0, false),
            ),
            keys,
        )
    }

    @Test
    fun `rejected presses are not released later`() {
        accepted = false

        router.send(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
        router.reset()

        assertEquals(listOf(SentKey(KeyEvent.KEYCODE_A, 0, true)), keys)
    }

    @Test
    fun `moving a collapsed selection sends arrow keys`() {
        router.send(AndroidImeEvent.SetSelection(0, 0))
        router.send(AndroidImeEvent.SetSelection(2, 2))

        assertEquals(
            keyStroke(KeyEvent.KEYCODE_DPAD_LEFT) + keyStroke(KeyEvent.KEYCODE_DPAD_RIGHT),
            keys,
        )
    }

    @Test
    fun `text is retyped as keys when no text input is focused`() {
        val router = fallbackRouter()

        router.send(AndroidImeEvent.CommitText("hi", 1))

        assertEquals(keyStroke(KeyEvent.KEYCODE_H) + keyStroke(KeyEvent.KEYCODE_I), keys)
        assertTrue(texts.isEmpty())
    }

    @Test
    fun `fallback types capitals with shift`() {
        val router = fallbackRouter()

        router.send(AndroidImeEvent.CommitText("Hi", 1))

        assertEquals(
            listOf(
                SentKey(KeyEvent.KEYCODE_SHIFT_LEFT, 0, true),
                SentKey(KeyEvent.KEYCODE_H, 0, true),
                SentKey(KeyEvent.KEYCODE_H, 0, false),
                SentKey(KeyEvent.KEYCODE_SHIFT_LEFT, 0, false),
            ) + keyStroke(KeyEvent.KEYCODE_I),
            keys,
        )
    }

    @Test
    fun `fallback keeps control characters as keys`() {
        val router = fallbackRouter()

        router.send(AndroidImeEvent.CommitText("a\nb", 1))

        assertEquals(
            keyStroke(KeyEvent.KEYCODE_A) +
                keyStroke(KeyEvent.KEYCODE_ENTER) +
                keyStroke(KeyEvent.KEYCODE_B),
            keys,
        )
    }

    @Test
    fun `fallback drops characters without a key`() {
        val router = fallbackRouter()

        router.send(AndroidImeEvent.CommitText("a你b", 1))

        assertEquals(keyStroke(KeyEvent.KEYCODE_A) + keyStroke(KeyEvent.KEYCODE_B), keys)
        assertTrue(texts.isEmpty())
    }

    private fun fallbackRouter(): DesktopImeRouter =
        DesktopImeRouter(
            keySink = { keyCode, scanCode, pressed ->
                keys += SentKey(keyCode, scanCode, pressed)
                accepted
            },
            textSink = { texts += it },
            textChannelAvailable = { false },
        )

    private fun key(
        action: Int,
        keyCode: Int,
        metaState: Int = 0,
        repeatCount: Int = 0,
        scanCode: Int = 0,
    ) = AndroidImeEvent.KeyEvent(
        action = action,
        keyCode = keyCode,
        scanCode = scanCode,
        eventTime = 1,
        metaState = metaState,
        repeatCount = repeatCount,
    )

    private fun keyStroke(keyCode: Int) = listOf(
        SentKey(keyCode, 0, true),
        SentKey(keyCode, 0, false),
    )

    private data class SentKey(val keyCode: Int, val scanCode: Int, val pressed: Boolean)
}
