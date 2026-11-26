package app.trierarch.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileComponentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(): FileComponentStore =
        FileComponentStore(temporaryFolder.newFolder("components"))

    @Test
    fun listsEntriesAndCanDeleteAnyOfThem() {
        val store = store()
        val rootfs = temporaryFolder.newFolder("rootfs")
        val root = store.rootfsComponents(rootfs).apply { mkdirs() }
        File(root, "kwin-compat").mkdirs()
        File(root, "libaquamarine-compat.so").writeText("lib")

        val entries = store.entries(rootfs)

        assertEquals(listOf("kwin-compat", "libaquamarine-compat.so"), entries.map { it.name })
        assertTrue(entries.first().directory)
        assertFalse(entries.last().directory)

        store.delete(rootfs, "kwin-compat")
        assertEquals(listOf("libaquamarine-compat.so"), store.entries(rootfs).map { it.name })
    }

    @Test
    fun copiesPlainFilesAndRefusesToOverwrite() {
        val store = store()
        val rootfs = temporaryFolder.newFolder("rootfs")
        val archive = File(temporaryFolder.newFolder("source"), "libfoo.so").apply {
            writeText("first")
        }

        assertEquals(listOf("libfoo.so"), store.install(rootfs, archive))
        assertEquals("first", File(store.rootfsComponents(rootfs), "libfoo.so").readText())

        archive.writeText("second")
        runCatching { store.install(rootfs, archive) }.onSuccess {
            throw AssertionError("install should refuse an existing entry")
        }
        assertEquals("first", File(store.rootfsComponents(rootfs), "libfoo.so").readText())
    }

    @Test
    fun availableArchivesAreJustTheFilesInTheDownloadDirectory() {
        val components = temporaryFolder.newFolder("downloads")
        val store = FileComponentStore(components)
        File(components, "index.toml").writeText("ignored")
        File(components, ".hidden").writeText("ignored")
        File(components, "one.tar.zst").writeText("one")
        File(components, "two.so").writeText("two")

        assertEquals(listOf("one.tar.zst", "two.so"), store.availableArchives().map { it.name })
    }

    @Test
    fun deletingAMissingEntryIsANoop() {
        val store = store()
        val rootfs = temporaryFolder.newFolder("rootfs")
        store.delete(rootfs, "missing")
        assertTrue(store.entries(rootfs).isEmpty())
    }
}
