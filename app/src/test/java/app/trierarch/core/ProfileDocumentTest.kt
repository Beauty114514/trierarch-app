package app.trierarch.core

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileDocumentTest {
    private val directory = File(System.getProperty("java.io.tmpdir"), "trierarch-profile-documents")

    @Before
    fun resetDirectory() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun summarisesDocumentsInFileNameOrder() {
        write(
            "b-wayland.toml",
            """
            id = "b-wayland"
            runtime = "proot"
            rootfs = "/rootfs"
            shell = "/bin/sh"

            [display]
            type = "wayland"

            [graphics]
            renderer = "llvmpipe"
            """.trimIndent(),
        )
        write("a-terminal.toml", "id = \"a-terminal\"\nruntime = \"internal-shell\"\n")

        val documents = ProfileService(directory).listDocuments()

        assertEquals(listOf("a-terminal", "b-wayland"), documents.map { it.id })
        assertEquals("internal-shell", documents[0].runtime)
        assertEquals(ProfileService.DISPLAY_NONE, documents[0].display)
        assertNull(documents[0].renderer)
        assertNull(documents[0].error)
        assertEquals("proot", documents[1].runtime)
        assertEquals("wayland", documents[1].display)
        assertEquals("llvmpipe", documents[1].renderer)
        assertNull(documents[1].error)
    }

    @Test
    fun reportsValidationErrorsWithoutThrowing() {
        write("broken.toml", "runtime = \"proot\"\n")

        val document = ProfileService(directory).listDocuments().single()

        assertEquals("broken", document.id)
        assertNotNull(document.error)
        assertTrue(document.error!!.contains("id must start with a lowercase letter"))
    }

    @Test
    fun anEmptyDirectoryProducesNoDocuments() {
        assertTrue(ProfileService(directory).listDocuments().isEmpty())
    }

    private fun write(name: String, text: String) {
        File(directory, name).writeText(text)
    }
}
