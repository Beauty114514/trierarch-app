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
    fun aLaterDownloadReplacesTheEntry() {
        val store = store()
        store.file("kwin.tar.zst").writeText("component")
        val next = "b".repeat(64)
        store.record("kwin-compat", "kwin.tar.zst", sha, downloadedAt = 1)
        store.record("kwin-compat", "kwin.tar.zst", next, downloadedAt = 2)

        val entries = store.downloaded()
        assertEquals(1, entries.size)
        assertEquals(next, entries.getValue("kwin-compat").sha256)
        assertEquals(2L, entries.getValue("kwin-compat").downloadedAt)
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
