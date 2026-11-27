package app.trierarch.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComponentStoreTest {
    private val sha = "a".repeat(64)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(): ComponentStore =
        ComponentStore(temporaryFolder.newFolder("components"))

    @Test
    fun recordsAndReadsDownloads() {
        val store = store()
        store.file("kwin.tar.zst").writeText("component")
        store.record("kwin-compat", "kwin.tar.zst", sha, downloadedAt = 123)

        val entry = store.downloaded().getValue("kwin-compat")
        assertEquals("kwin-compat", entry.id)
        assertEquals("kwin.tar.zst", entry.file)
        assertEquals(sha, entry.sha256)
        assertEquals(123L, entry.downloadedAt)
        assertTrue(java.io.File(store.file("index.toml").parentFile, "index.toml").isFile)
    }

    @Test
    fun aLaterDownloadReplacesThePreviousArchive() {
        val store = store()
        val old = store.file("kwin-old.tar.zst").apply { writeText("old") }
        val current = store.file("kwin-new.tar.zst").apply { writeText("new") }
        val next = "b".repeat(64)
        store.record("kwin-compat", old.name, sha, downloadedAt = 1, version = "1")

        store.record("kwin-compat", current.name, next, downloadedAt = 2, version = "2")

        val entry = store.downloaded().getValue("kwin-compat")
        assertEquals(current.name, entry.file)
        assertEquals(next, entry.sha256)
        assertEquals(2L, entry.downloadedAt)
        assertTrue(!old.exists())
        assertTrue(current.exists())
    }

    @Test
    fun anOldFileSharedWithAnotherComponentIsKept() {
        val store = store()
        val shared = store.file("shared.tar.zst").apply { writeText("shared") }
        store.record("kwin-compat", shared.name, sha)
        store.record("other-component", shared.name, sha)
        val replacement = store.file("kwin-new.tar.zst").apply { writeText("new") }

        store.record("kwin-compat", replacement.name, "b".repeat(64))

        assertTrue(shared.exists())
        assertEquals(replacement.name, store.downloaded().getValue("kwin-compat").file)
        assertEquals(shared.name, store.downloaded().getValue("other-component").file)
    }

    @Test
    fun removeDeletesTheFileAndItsEntry() {
        val store = store()
        val file = store.file("kwin.tar.zst").apply { writeText("component") }
        store.record("kwin-compat", "kwin.tar.zst", sha)

        val removed = store.remove("kwin-compat")

        assertEquals("kwin.tar.zst", removed?.file)
        assertTrue(!file.exists())
        assertTrue(store.downloaded().isEmpty())
    }

    @Test
    fun removeUnknownReturnsNull() {
        assertNull(store().remove("missing"))
    }

    @Test
    fun removeAlsoDropsAnEntryWhoseFileIsGone() {
        val store = store()
        val file = store.file("kwin.tar.zst").apply { writeText("component") }
        store.record("kwin-compat", "kwin.tar.zst", sha)
        file.delete()

        assertEquals("kwin.tar.zst", store.remove("kwin-compat")?.file)
        assertTrue(store.downloaded().isEmpty())
    }

    @Test
    fun aMissingOrCorruptIndexIsEmpty() {
        val store = store()
        assertTrue(store.downloaded().isEmpty())

        java.io.File(store.file("index.toml").parentFile, "index.toml").writeText("not [ toml")
        assertTrue(store.downloaded().isEmpty())
        assertNull(store.downloaded()["kwin-compat"])
    }
}
