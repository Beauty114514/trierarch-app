package app.trierarch.feature.rootfs

import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RootfsControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val direct = Executor { runnable -> runnable.run() }

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
    fun loadsTheCatalogAndRecordsASuccessfulDownload() {
        val store = store()
        val image = image()
        val controller = RootfsController(
            store = store,
            loadImages = { listOf(image) },
            downloadFile = { _, destination, progress, _ ->
                destination.parentFile?.mkdirs()
                destination.writeText("archive")
                progress(3, 3)
                destination
            },
            executor = direct,
        )

        controller.load()
        assertEquals(listOf(image), controller.state.value.images)

        controller.download(image)

        assertEquals(1, controller.state.value.downloaded.size)
        assertEquals(image.fileName, controller.state.value.downloaded.single().file)
        assertTrue(store.fileFor(image).isFile)
        assertTrue(controller.state.value.downloads.isEmpty())
    }

    @Test
    fun aFailedDownloadIsShownAndLeavesNoArchive() {
        val store = store()
        val image = image()
        val controller = RootfsController(
            store = store,
            loadImages = { listOf(image) },
            downloadFile = { _, _, _, _ -> throw IOException("boom") },
            executor = direct,
        )

        controller.load()
        controller.download(image)

        val failure = controller.state.value.downloads[image.product]
        assertTrue(failure is RootfsState.DownloadState.Failed)
        assertFalse(store.fileFor(image).exists())
        assertTrue(controller.state.value.downloaded.isEmpty())
    }
}
