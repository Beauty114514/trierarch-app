package app.trierarch.update

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DigestsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun hashesBytes() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            sha256Hex("hello".toByteArray()),
        )
    }

    @Test
    fun hashesFiles() {
        val file = temporaryFolder.newFile("apk")
        file.writeText("hello")
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            sha256Hex(file),
        )
    }
}
