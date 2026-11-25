package app.trierarch.feature.rootfs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RootfsDownloadStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun image() = LxcImage(
        product = "alpine:3.22:arm64:default",
        os = "Alpine",
        release = "3.22",
        variant = "default",
        arch = "arm64",
        buildDate = "20261117_13:01",
        downloadUrl = "https://example.com/rootfs.tar.xz",
        size = 20,
        sha256 = "abc",
    )

    private fun store(): RootfsDownloadStore =
        RootfsDownloadStore(temporaryFolder.newFolder("downloads", "rootfs"))

    @Test
    fun recordsListsAndRemovesAnArchive() {
        val store = store()
        val image = image()
        val file = store.fileFor(image).apply {
            parentFile?.mkdirs()
            writeText("archive")
        }

        store.record(image, file)

        val entry = store.list().single()
        assertEquals(image.fileName, entry.file)
        assertEquals(image.product, entry.product)
        assertEquals(image.downloadUrl, entry.source)
        assertEquals(7L, entry.size)
        assertTrue(store.indexFile().isFile)

        val removed = store.remove(image.fileName)
        assertNotNull(removed)
        assertTrue(store.list().isEmpty())
        assertTrue(!file.exists())
    }

    @Test
    fun hidesEntriesWhoseArchiveDisappeared() {
        val store = store()
        val image = image()
        val file = store.fileFor(image).apply {
            parentFile?.mkdirs()
            writeText("archive")
        }
        store.record(image, file)
        file.delete()

        assertTrue(store.list().isEmpty())
        assertNotNull(store.remove(image.fileName))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun corruptIndexIsTreatedAsEmpty() {
        val store = store()
        store.indexFile().parentFile?.mkdirs()
        store.indexFile().writeText("not toml [[[")

        assertTrue(store.list().isEmpty())
    }
}
