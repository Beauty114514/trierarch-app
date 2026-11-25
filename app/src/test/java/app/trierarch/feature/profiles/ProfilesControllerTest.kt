package app.trierarch.feature.profiles

import android.content.ContextWrapper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilesControllerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var core: FakeCore
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        core = FakeCore()
    }

    private fun controller() = ProfilesController(
        context = ContextWrapper(null),
        core = core,
        scope = scope,
    )

    private fun document(id: String = "arch") = ProfileDocument(
        file = File("/tmp/$id.toml"),
        id = id,
        runtime = "proot",
        display = "x11",
        renderer = null,
        error = null,
    )

    @Test
    fun initReadsDocumentsAndSessions() {
        core.documents += document("arch")
        core.sessions = RuntimeSnapshot(
            mapOf("arch" to SessionSnapshot("arch", SessionPhase.RUNNING, true)),
            null,
        )

        val controller = controller()

        assertEquals(listOf("arch"), controller.state.value.documents.map { it.id })
        assertEquals(
            SessionPhase.RUNNING,
            controller.state.value.sessions.sessions["arch"]?.phase,
        )
    }

    @Test
    fun refreshListsDroidspacesContainers() = runBlocking {
        val controller = ProfilesController(
            context = ContextWrapper(null),
            core = core,
            scope = scope,
            listDroidspacesContainers = { listOf("Debian", "Arch", "Arch") },
        )

        controller.refreshDroidspacesContainers()

        val state = withTimeout(2_000) {
            controller.state.first {
                !it.droidspacesLoading && it.droidspacesContainers.isNotEmpty()
            }
        }
        assertEquals(listOf("Arch", "Debian"), state.droidspacesContainers)
    }

    @Test
    fun openDetailsNavigatesAndUnknownIdSetsError() {
        core.documents += document()
        val controller = controller()

        controller.openDetails("arch")
        assertEquals(ProfileScreen.Details("arch"), controller.state.value.screen)

        controller.openDetails("nope")
        assertTrue(controller.state.value.error?.contains("does not exist") == true)
    }

    @Test
    fun editorLoadsAndSavesWithTheOpenedRevision() {
        core.documents += document()
        core.readResult = DocumentContent("arch", "old", "rev-1")
        val controller = controller()

        controller.openEditor("arch")
        assertEquals(ProfileScreen.Editor("arch"), controller.state.value.screen)
        assertEquals("old", controller.state.value.editorText)

        controller.saveEditor("new")

        assertEquals(Command.Document.Save("arch", "new", "rev-1"), core.submitted.last())
        assertEquals(ProfileScreen.List, controller.state.value.screen)
    }

    @Test
    fun saveFailureKeepsTheEditorAndShowsTheError() {
        core.documents += document()
        core.readResult = DocumentContent("arch", "old", "rev-1")
        core.outcome = CommandOutcome.Failure(CommandError.InvalidDocument("arch", "bad"))
        val controller = controller()
        controller.openEditor("arch")

        controller.saveEditor("new")

        assertEquals(ProfileScreen.Editor("arch"), controller.state.value.screen)
        assertTrue(controller.state.value.error?.contains("invalid") == true)
    }

    @Test
    fun backWalksEditorDetailsListAndStopsAtTheList() {
        core.documents += document()
        core.readResult = DocumentContent("arch", "old", "rev-1")
        val controller = controller()
        controller.openEditor("arch")

        assertTrue(controller.back())
        assertEquals(ProfileScreen.Details("arch"), controller.state.value.screen)

        assertTrue(controller.back())
        assertEquals(ProfileScreen.List, controller.state.value.screen)

        assertFalse(controller.back())
    }

    @Test
    fun runSuccessCallsAfterReplyAndEmitsFollowSession() = runBlocking {
        core.documents += document()
        var afterReply = false
        core.outcome = CommandOutcome.Success("ok", afterReply = { afterReply = true })
        val controller = controller()

        val received = async(Dispatchers.Unconfined) { controller.events.first() }
        controller.run("arch")

        assertTrue(afterReply)
        assertEquals(ProfilesEvent.FollowSession, received.await())
        assertEquals(Command.Session.Run("arch"), core.submitted.last())
    }

    @Test
    fun stopSuccessSubmitsStop() {
        core.documents += document()
        val controller = controller()

        controller.stop("arch")

        assertEquals(Command.Session.Stop("arch"), core.submitted.last())
    }

    @Test
    fun coreEventRefreshesTheDocuments() {
        core.documents += document("arch")
        val controller = controller()
        core.documents += document("debian")

        core.listener?.invoke(CoreEvent.DocumentsChanged)

        assertEquals(listOf("arch", "debian"), controller.state.value.documents.map { it.id })
    }

    @Test
    fun closeRemovesTheCoreListener() {
        val controller = controller()
        assertNotNull(core.listener)

        controller.close()

        assertNull(core.listener)
    }

    @Test
    fun openLogsLoadsTheLogText() {
        core.documents += document()
        core.logs["arch"] = "line"
        val controller = controller()

        controller.openLogs("arch")

        assertEquals(ProfileScreen.Logs("arch"), controller.state.value.screen)
        assertEquals("line", controller.state.value.logText)
    }

    @Test
    fun deleteSubmitsTheRevisionReadFromTheCore() {
        core.documents += document()
        core.readResult = DocumentContent("arch", "text", "rev-9")
        val controller = controller()

        controller.delete("arch")

        assertEquals(Command.Document.Delete("arch", "rev-9"), core.submitted.last())
        assertEquals(ProfileScreen.List, controller.state.value.screen)
    }

    private fun rootfs(): File = temporaryFolder.newFolder("alpine").apply {
        File(this, "etc").mkdirs()
        File(this, "etc/os-release").writeText("ID=alpine\n")
        File(this, "bin").mkdirs()
        File(this, "bin/sh").writeText("")
    }

    @Test
    fun createDraftSubmitsCreateAndSaveAndEmitsProfileCreated() = runBlocking {
        val rootfs = rootfs()
        core.readResult = DocumentContent("alpine-demo", "template", "rev-1")
        val controller = controller()
        val event = async(Dispatchers.Unconfined) { controller.events.first() }

        controller.create(
            ProfileDraft.Proot(
                id = "alpine-demo",
                rootfs = rootfs.absolutePath,
                shell = "/bin/sh",
            ),
        )

        assertEquals(2, core.submitted.size)
        assertTrue(core.submitted[0] is Command.Document.Create)
        val save = core.submitted[1] as Command.Document.Save
        assertEquals("alpine-demo", save.id)
        assertEquals("rev-1", save.expectedRevision)
        assertTrue(save.text.contains("runtime = \"proot\""))
        assertTrue(save.text.contains("rootfs = \"${rootfs.absolutePath}\""))
        assertEquals(ProfilesEvent.ProfileCreated, event.await())
        assertEquals(ProfileScreen.List, controller.state.value.screen)
    }

    @Test
    fun createDraftValidationFailureDoesNotSubmit() {
        val controller = controller()

        controller.create(
            ProfileDraft.Proot(
                id = "alpine-demo",
                rootfs = "/does/not/exist",
                shell = "/bin/sh",
            ),
        )

        assertTrue(core.submitted.isEmpty())
        assertTrue(controller.state.value.error?.contains("rootfs") == true)
    }

    @Test
    fun createDraftSaveFailureDeletesTheTemplate() {
        val rootfs = rootfs()
        core.readResult = DocumentContent("alpine-demo", "template", "rev-2")
        core.outcomes.add(CommandOutcome.Success("created"))
        core.outcomes.add(CommandOutcome.Failure(CommandError.InvalidDocument("alpine-demo", "bad")))
        val controller = controller()

        controller.create(
            ProfileDraft.Proot(
                id = "alpine-demo",
                rootfs = rootfs.absolutePath,
                shell = "/bin/sh",
            ),
        )

        assertEquals(3, core.submitted.size)
        assertTrue(core.submitted[0] is Command.Document.Create)
        assertTrue(core.submitted[1] is Command.Document.Save)
        assertEquals(
            Command.Document.Delete("alpine-demo", "rev-2"),
            core.submitted[2],
        )
        assertTrue(controller.state.value.error?.contains("invalid") == true)
    }

    @Test
    fun createDraftDroidspacesDoesNotNeedARootfs() {
        core.readResult = DocumentContent("arch-ds", "template", "rev-3")
        val controller = controller()

        controller.create(
            ProfileDraft.Droidspaces(
                id = "arch-ds",
                container = "Arch",
                user = "beauty",
            ),
        )

        assertEquals(2, core.submitted.size)
        val save = core.submitted[1] as Command.Document.Save
        assertTrue(save.text.contains("runtime = \"droidspaces\""))
        assertTrue(save.text.contains("container = \"Arch\""))
        assertTrue(save.text.contains("user = \"beauty\""))
        assertFalse(save.text.contains("rootfs"))
    }

    private class FakeCore : CoreApi {
        val documents = mutableListOf<ProfileDocument>()
        var sessions = RuntimeSnapshot(emptyMap(), null)
        val logs = mutableMapOf<String, String>()
        var readResult: DocumentContent? = null
        var outcome: CommandOutcome = CommandOutcome.Success("ok")
        val submitted = mutableListOf<Command>()
        val outcomes = java.util.ArrayDeque<CommandOutcome>()
        var listener: ((CoreEvent) -> Unit)? = null

        override fun documents(): List<ProfileDocument> = documents.toList()
        override fun read(id: String): DocumentContent? = readResult
        override fun sessions(): RuntimeSnapshot = sessions
        override fun logs(id: String, lines: Int): String? = logs[id]
        override fun submit(command: Command, onDone: (CommandOutcome) -> Unit) {
            submitted += command
            onDone(outcomes.pollFirst() ?: outcome)
        }

        override fun addListener(listener: (CoreEvent) -> Unit) {
            this.listener = listener
        }

        override fun removeListener(listener: (CoreEvent) -> Unit) {
            if (this.listener === listener) this.listener = null
        }
    }
}
