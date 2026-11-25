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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfilesControllerTest {
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

    private class FakeCore : CoreApi {
        val documents = mutableListOf<ProfileDocument>()
        var sessions = RuntimeSnapshot(emptyMap(), null)
        val logs = mutableMapOf<String, String>()
        var readResult: DocumentContent? = null
        var outcome: CommandOutcome = CommandOutcome.Success("ok")
        val submitted = mutableListOf<Command>()
        var listener: ((CoreEvent) -> Unit)? = null

        override fun documents(): List<ProfileDocument> = documents.toList()
        override fun read(id: String): DocumentContent? = readResult
        override fun sessions(): RuntimeSnapshot = sessions
        override fun logs(id: String, lines: Int): String? = logs[id]
        override fun submit(command: Command, onDone: (CommandOutcome) -> Unit) {
            submitted += command
            onDone(outcome)
        }

        override fun addListener(listener: (CoreEvent) -> Unit) {
            this.listener = listener
        }

        override fun removeListener(listener: (CoreEvent) -> Unit) {
            if (this.listener === listener) this.listener = null
        }
    }
}
