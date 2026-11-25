package app.trierarch.feature.rootfs

import app.trierarch.core.Command
import app.trierarch.core.CommandError
import app.trierarch.core.CommandOutcome
import app.trierarch.core.CoreApi
import app.trierarch.core.CoreEvent
import app.trierarch.core.DocumentContent
import app.trierarch.core.ProfileDocument
import app.trierarch.core.RuntimeSnapshot
import app.trierarch.core.SessionPhase
import app.trierarch.core.SessionSnapshot
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

    private fun controller(
        store: RootfsDownloadStore,
        core: FakeCore,
        rootfsDirectory: File,
        downloadFile: (LxcImage, File, (Long, Long) -> Unit, () -> Boolean) -> File,
    ) = RootfsController(
        store = store,
        rootfsDirectory = rootfsDirectory,
        core = core,
        loadImages = { listOf(image()) },
        downloadFile = downloadFile,
        executor = direct,
    )

    private fun downloaded(
        controller: RootfsController,
        store: RootfsDownloadStore,
        image: LxcImage,
    ): DownloadedRootfs {
        controller.download(image)
        return controller.state.value.downloaded.single()
    }

    @Test
    fun loadsTheCatalogAndRecordsASuccessfulDownload() {
        val store = store()
        val controller = controller(
            store = store,
            core = FakeCore(),
            rootfsDirectory = temporaryFolder.newFolder("rootfs"),
            downloadFile = { _, destination, progress, _ ->
                destination.parentFile?.mkdirs()
                destination.writeText("archive")
                progress(3, 3)
                destination
            },
        )

        controller.load()
        assertEquals(listOf(image()), controller.state.value.images)

        controller.download(image())

        assertEquals(1, controller.state.value.downloaded.size)
        assertEquals(image().fileName, controller.state.value.downloaded.single().file)
        assertTrue(store.fileFor(image()).isFile)
        assertTrue(controller.state.value.downloads.isEmpty())
    }

    @Test
    fun aFailedDownloadIsShownAndLeavesNoArchive() {
        val store = store()
        val controller = controller(
            store = store,
            core = FakeCore(),
            rootfsDirectory = temporaryFolder.newFolder("rootfs"),
            downloadFile = { _, _, _, _ -> throw IOException("boom") },
        )

        controller.load()
        controller.download(image())

        val failure = controller.state.value.downloads[image().product]
        assertTrue(failure is RootfsState.DownloadState.Failed)
        assertFalse(store.fileFor(image()).exists())
        assertTrue(controller.state.value.downloaded.isEmpty())
    }

    @Test
    fun installSubmitsImportKeepsTheArchiveAndRecordsThePath() {
        val store = store()
        val rootfs = temporaryFolder.newFolder("rootfs")
        val core = FakeCore().apply {
            onCommand = { command ->
                if (command is Command.Document.Import) {
                    File(rootfs, command.name).mkdirs()
                }
            }
        }
        val controller = controller(
            store = store,
            core = core,
            rootfsDirectory = rootfs,
            downloadFile = { _, destination, _, _ ->
                destination.parentFile?.mkdirs()
                destination.writeText("archive")
                destination
            },
        )
        val image = image()
        val downloaded = downloaded(controller, store, image)

        controller.install(downloaded, "alpine")

        assertEquals(
            listOf(Command.Document.Import(store.fileFor(image).absolutePath, "alpine")),
            core.commands,
        )
        assertTrue(store.fileFor(image).isFile)
        val installed = controller.state.value.installs[downloaded.file]
        assertTrue(installed is RootfsState.InstallState.Installed)
        assertEquals(File(rootfs, "alpine").absolutePath, (installed as RootfsState.InstallState.Installed).path)
        assertEquals(listOf("alpine"), controller.state.value.installed.map { it.name })
    }

    @Test
    fun renamesAnUnusedInstalledRootfs() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        File(rootfs, "arch").mkdirs()
        val core = FakeCore()
        val controller = controller(
            store = store(),
            core = core,
            rootfsDirectory = rootfs,
            downloadFile = { _, _, _, _ -> error("not used") },
        )
        val entry = controller.state.value.installed.single()

        controller.renameInstalled(entry, "debian")

        assertTrue(File(rootfs, "debian").isDirectory)
        assertFalse(File(rootfs, "arch").exists())
        assertEquals(listOf("debian"), controller.state.value.installed.map { it.name })
        assertTrue(core.commands.isEmpty())
    }

    @Test
    fun rejectsRenameWhenAProfileReferencesTheRootfs() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        File(rootfs, "arch").mkdirs()
        val core = FakeCore()
        core.addProfile("arch-demo", File(rootfs, "arch").absolutePath)
        val controller = controller(
            store = store(),
            core = core,
            rootfsDirectory = rootfs,
            downloadFile = { _, _, _, _ -> error("not used") },
        )

        controller.renameInstalled(controller.state.value.installed.single(), "debian")

        assertTrue(File(rootfs, "arch").isDirectory)
        assertFalse(File(rootfs, "debian").exists())
        assertTrue(controller.state.value.error?.contains("referenced") == true)
    }

    @Test
    fun rejectsRenameWhenARunningProfileUsesTheRootfs() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        File(rootfs, "arch").mkdirs()
        val core = FakeCore()
        core.addProfile("arch-demo", File(rootfs, "arch").absolutePath)
        core.sessions = RuntimeSnapshot(
            mapOf("arch-demo" to SessionSnapshot("arch-demo", SessionPhase.RUNNING, true)),
            null,
        )
        val controller = controller(
            store = store(),
            core = core,
            rootfsDirectory = rootfs,
            downloadFile = { _, _, _, _ -> error("not used") },
        )

        controller.renameInstalled(controller.state.value.installed.single(), "debian")

        assertTrue(controller.state.value.error?.contains("running") == true)
    }

    @Test
    fun deletesAnUnusedInstalledRootfs() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        File(rootfs, "arch").mkdirs()
        val core = FakeCore().apply {
            onCommand = { command ->
                if (command is Command.Document.Remove) {
                    File(rootfs, "arch").deleteRecursively()
                }
            }
        }
        val controller = controller(
            store = store(),
            core = core,
            rootfsDirectory = rootfs,
            downloadFile = { _, _, _, _ -> error("not used") },
        )

        controller.deleteInstalled(controller.state.value.installed.single())

        assertEquals(listOf(Command.Document.Remove("arch")), core.commands)
        assertTrue(controller.state.value.installed.isEmpty())
    }

    @Test
    fun rejectsDeleteWhenAProfileReferencesTheRootfs() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        File(rootfs, "arch").mkdirs()
        val core = FakeCore()
        core.addProfile("arch-demo", File(rootfs, "arch").absolutePath)
        val controller = controller(
            store = store(),
            core = core,
            rootfsDirectory = rootfs,
            downloadFile = { _, _, _, _ -> error("not used") },
        )

        controller.deleteInstalled(controller.state.value.installed.single())

        assertTrue(core.commands.isEmpty())
        assertTrue(File(rootfs, "arch").isDirectory)
        assertTrue(controller.state.value.error?.contains("referenced") == true)
    }

    @Test
    fun refreshInstalledListsOnlyVisibleDirectoriesInTheDefaultPath() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        File(rootfs, "debian").mkdirs()
        File(rootfs, "arch").mkdirs()
        File(rootfs, ".alpine.import-1").mkdirs()
        File(rootfs, "README").writeText("not a rootfs")
        val controller = controller(
            store = store(),
            core = FakeCore(),
            rootfsDirectory = rootfs,
            downloadFile = { _, _, _, _ -> error("not used") },
        )

        controller.refreshInstalled()

        assertEquals(listOf("arch", "debian"), controller.state.value.installed.map { it.name })
        assertEquals(File(rootfs, "arch").absolutePath, controller.state.value.installed.first().path)
    }

    @Test
    fun duplicateRootfsNameIsRejectedBeforeSubmitting() {
        val store = store()
        val rootfs = temporaryFolder.newFolder("rootfs")
        File(rootfs, "alpine").mkdirs()
        val core = FakeCore()
        val controller = controller(
            store = store,
            core = core,
            rootfsDirectory = rootfs,
            downloadFile = { _, destination, _, _ ->
                destination.parentFile?.mkdirs()
                destination.writeText("archive")
                destination
            },
        )
        val downloaded = downloaded(controller, store, image())

        controller.install(downloaded, "alpine")

        assertTrue(core.commands.isEmpty())
        val failed = controller.state.value.installs[downloaded.file]
        assertTrue(failed is RootfsState.InstallState.Failed)
        assertTrue((failed as RootfsState.InstallState.Failed).message.contains("already exists"))
        assertTrue(store.fileFor(image()).isFile)
    }

    @Test
    fun invalidNameIsRejectedBeforeSubmitting() {
        val store = store()
        val core = FakeCore()
        val controller = controller(
            store = store,
            core = core,
            rootfsDirectory = temporaryFolder.newFolder("rootfs"),
            downloadFile = { _, destination, _, _ ->
                destination.parentFile?.mkdirs()
                destination.writeText("archive")
                destination
            },
        )
        val downloaded = downloaded(controller, store, image())

        controller.install(downloaded, "Alpine")

        assertTrue(core.commands.isEmpty())
        assertTrue(controller.state.value.installs[downloaded.file] is RootfsState.InstallState.Failed)
    }

    @Test
    fun installFailureKeepsTheArchive() {
        val store = store()
        val core = FakeCore().apply {
            outcome = CommandOutcome.Failure(CommandError.Failed("boom"))
        }
        val controller = controller(
            store = store,
            core = core,
            rootfsDirectory = temporaryFolder.newFolder("rootfs"),
            downloadFile = { _, destination, _, _ ->
                destination.parentFile?.mkdirs()
                destination.writeText("archive")
                destination
            },
        )
        val downloaded = downloaded(controller, store, image())

        controller.install(downloaded, "alpine")

        assertEquals(1, core.commands.size)
        assertTrue(controller.state.value.installs[downloaded.file] is RootfsState.InstallState.Failed)
        assertTrue(store.fileFor(image()).isFile)
    }

    private class FakeCore : CoreApi {
        val commands = mutableListOf<Command>()
        var outcome: CommandOutcome = CommandOutcome.Success("ok")
        var onCommand: ((Command) -> Unit)? = null
        private val profiles = mutableListOf<ProfileDocument>()
        private val contents = mutableMapOf<String, String>()
        var sessions: RuntimeSnapshot = RuntimeSnapshot(emptyMap(), null)

        fun addProfile(id: String, rootfs: String) {
            profiles += ProfileDocument(
                file = File("/tmp/$id.toml"),
                id = id,
                runtime = "proot",
                display = "none",
                renderer = null,
                error = null,
            )
            contents[id] = "runtime = \"proot\"\nrootfs = \"$rootfs\"\nshell = \"/bin/sh\"\n"
        }

        override fun documents(): List<ProfileDocument> = profiles.toList()
        override fun read(id: String): DocumentContent? =
            contents[id]?.let { DocumentContent(id, it, "rev") }

        override fun sessions(): RuntimeSnapshot = sessions
        override fun logs(id: String, lines: Int): String? = null
        override fun submit(command: Command, onDone: (CommandOutcome) -> Unit) {
            commands += command
            onCommand?.invoke(command)
            onDone(outcome)
        }

        override fun addListener(listener: (CoreEvent) -> Unit) = Unit
        override fun removeListener(listener: (CoreEvent) -> Unit) = Unit
    }
}
