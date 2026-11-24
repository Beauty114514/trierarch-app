package app.trierarch.core

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileServiceDocumentTest {
    private val directory = File(System.getProperty("java.io.tmpdir"), "trierarch-profile-crud")

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
    fun createsTheTemplateAndRefusesDuplicates() {
        val service = ProfileService(directory)

        assertEquals("arch-demo", service.create("arch-demo"))
        val document = service.readDocument("arch-demo")
        assertNotNull(document)
        assertTrue(document!!.text.contains("id = \"arch-demo\""))
        assertTrue(document.text.contains("runtime = \"proot\""))

        val error = assertThrows(ProfileServiceException::class.java) {
            service.create("arch-demo")
        }
        assertEquals(CommandError.AlreadyExists("arch-demo"), error.error)
    }

    @Test
    fun savesOnlyWithTheCurrentRevision() {
        val service = ProfileService(directory)
        service.create("arch-demo")
        val before = service.readDocument("arch-demo")!!

        val edited = before.text.replace("type = \"none\"", "type = \"wayland\"")
        assertEquals("arch-demo", service.save("arch-demo", edited, before.revision))

        val after = service.readDocument("arch-demo")!!
        assertNotEquals(before.revision, after.revision)
        assertTrue(after.text.contains("type = \"wayland\""))
    }

    @Test
    fun rejectsAStaleRevision() {
        val service = ProfileService(directory)
        service.create("arch-demo")
        val before = service.readDocument("arch-demo")!!
        val stale = before.revision
        service.save("arch-demo", before.text + "\n", stale)

        val error = assertThrows(ProfileServiceException::class.java) {
            service.save("arch-demo", before.text, stale)
        }
        assertEquals(CommandError.RevisionMismatch("arch-demo"), error.error)
    }

    @Test
    fun renamingTheIdMovesTheFile() {
        val service = ProfileService(directory)
        service.create("arch-demo")
        val before = service.readDocument("arch-demo")!!
        val renamed = before.text.replace("id = \"arch-demo\"", "id = \"arch-renamed\"")

        assertEquals("arch-renamed", service.save("arch-demo", renamed, before.revision))
        assertNull(service.readDocument("arch-demo"))
        assertNotNull(service.readDocument("arch-renamed"))
    }

    @Test
    fun deletesOnlyWithTheCurrentRevision() {
        val service = ProfileService(directory)
        service.create("arch-demo")
        val document = service.readDocument("arch-demo")!!

        val error = assertThrows(ProfileServiceException::class.java) {
            service.delete("arch-demo", "stale")
        }
        assertEquals(CommandError.RevisionMismatch("arch-demo"), error.error)

        service.delete("arch-demo", document.revision)
        assertNull(service.readDocument("arch-demo"))
    }
}
