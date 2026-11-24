package app.trierarch.terminal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionLogTailTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun keepsTheLastLines() {
        val file = temporaryFolder.newFile("session.log")
        file.writeText("one\ntwo\nthree\nfour\n")

        assertEquals("three\nfour", tailSessionLog(file, 2))
    }

    @Test
    fun returnsAllLinesWhenTheLogIsShorter() {
        val file = temporaryFolder.newFile("session.log")
        file.writeText("one\ntwo\n")

        assertEquals("one\ntwo", tailSessionLog(file, 10))
    }

    @Test
    fun aMissingLogReturnsNull() {
        assertNull(tailSessionLog(File(temporaryFolder.root, "missing.log"), 10))
    }

    @Test
    fun stripsAnsiSequencesAndCarriageReturns() {
        val file = temporaryFolder.newFile("session.log")
        file.writeText("\u001B[31mred\u001B[0m\r\nsecond\n")

        assertEquals("red\nsecond", tailSessionLog(file, 10))
    }

    @Test
    fun trimsAPartialFirstLineAtTheWindowEdge() {
        val file = temporaryFolder.newFile("big.log")
        file.writeText("x".repeat(300_000) + "\nlast\n")

        assertEquals("last", tailSessionLog(file, 10))
    }
}
