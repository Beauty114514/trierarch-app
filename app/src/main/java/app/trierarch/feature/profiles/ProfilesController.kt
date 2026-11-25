package app.trierarch.feature.profiles

import android.content.Context
import android.net.Uri
import app.trierarch.core.Command
import app.trierarch.core.CommandOutcome
import app.trierarch.core.Core
import app.trierarch.core.CoreEvent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profile-screen backend: re-reads Core, owns the List/Details/Editor/Logs
 * navigation and submits commands. The GUI only renders [state] and forwards
 * intents; it never talks to Core or touches files.
 */
class ProfilesController(
    private val context: Context,
    private val core: Core,
) {
    private val mutableState = MutableStateFlow(ProfilesState())
    private val mutableEvents = MutableSharedFlow<ProfilesEvent>(extraBufferCapacity = 8)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coreListener: (CoreEvent) -> Unit = { refresh() }

    val state: StateFlow<ProfilesState> = mutableState
    val events: SharedFlow<ProfilesEvent> = mutableEvents

    init {
        core.addListener(coreListener)
        refresh()
    }

    fun close() {
        core.removeListener(coreListener)
        scope.cancel()
    }

    /** Returns true when it navigated inside the profile screens. */
    fun back(): Boolean {
        val current = mutableState.value.screen
        return when (current) {
            ProfileScreen.List -> false
            is ProfileScreen.Details -> {
                navigate(ProfileScreen.List)
                true
            }
            is ProfileScreen.Editor -> {
                navigate(ProfileScreen.Details(current.id))
                true
            }
            is ProfileScreen.Logs -> {
                navigate(ProfileScreen.Details(current.id))
                true
            }
        }
    }

    fun openDetails(id: String) {
        if (mutableState.value.documents.none { it.id == id }) {
            setError("profile does not exist: $id")
            return
        }
        navigate(ProfileScreen.Details(id))
    }

    fun openEditor(id: String) {
        val document = core.read(id)
        if (document == null) {
            setError("profile does not exist: $id")
            return
        }
        mutableState.value = mutableState.value.copy(
            screen = ProfileScreen.Editor(id),
            editorText = document.text,
            editorRevision = document.revision,
            error = null,
        )
    }

    fun saveEditor(text: String) {
        val current = mutableState.value
        val screen = current.screen as? ProfileScreen.Editor ?: return
        submit(Command.Document.Save(screen.id, text, current.editorRevision)) { outcome ->
            when (outcome) {
                is CommandOutcome.Failure -> setError(outcome.error.message)
                is CommandOutcome.Success -> {
                    navigate(ProfileScreen.List)
                    refresh()
                }
            }
        }
    }

    fun openLogs(id: String) {
        val text = core.logs(id, 400) ?: "No session log yet."
        mutableState.value = mutableState.value.copy(
            screen = ProfileScreen.Logs(id),
            logText = text,
            error = null,
        )
    }

    fun create(id: String) {
        submit(Command.Document.Create(id)) { outcome ->
            when (outcome) {
                is CommandOutcome.Failure -> setError(outcome.error.message)
                is CommandOutcome.Success -> {
                    navigate(ProfileScreen.List)
                    refresh()
                }
            }
        }
    }

    fun delete(id: String) {
        val document = core.read(id)
        if (document == null) {
            setError("profile does not exist: $id")
            return
        }
        submit(Command.Document.Delete(id, document.revision)) { outcome ->
            when (outcome) {
                is CommandOutcome.Failure -> setError(outcome.error.message)
                is CommandOutcome.Success -> {
                    navigate(ProfileScreen.List)
                    refresh()
                }
            }
        }
    }

    fun run(id: String) = session(Command.Session.Run(id))

    fun open(id: String) = session(Command.Session.Open(id))

    fun stop(id: String) = session(Command.Session.Stop(id))

    fun importUri(uri: Uri, name: String) {
        scope.launch {
            val archive = runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(context.cacheDir, "import-${System.nanoTime()}.tar.xz")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("unable to open archive")
                    file
                }
            }.getOrElse {
                setError(it.message ?: "unable to read archive")
                return@launch
            }
            core.submit(Command.Document.Import(archive.absolutePath, name)) { outcome ->
                archive.delete()
                when (outcome) {
                    is CommandOutcome.Failure -> setError(outcome.error.message)
                    is CommandOutcome.Success -> {
                        mutableState.value = mutableState.value.copy(error = null)
                        mutableEvents.tryEmit(ProfilesEvent.Message(outcome.message))
                        refresh()
                    }
                }
            }
        }
    }

    /** Re-reads Core; safe to call from onResume and Core events. */
    fun refresh() {
        val documents = core.documents()
        val sessions = core.sessions()
        val screen = when (val current = mutableState.value.screen) {
            is ProfileScreen.Details ->
                if (documents.any { it.id == current.id }) current else ProfileScreen.List
            is ProfileScreen.Editor ->
                if (documents.any { it.id == current.id }) current else ProfileScreen.List
            is ProfileScreen.Logs ->
                if (documents.any { it.id == current.id }) current else ProfileScreen.List
            ProfileScreen.List -> ProfileScreen.List
        }
        mutableState.value = mutableState.value.copy(
            documents = documents,
            sessions = sessions,
            screen = screen,
        )
    }

    private fun session(command: Command.Session) {
        submit(command) { outcome ->
            when (outcome) {
                is CommandOutcome.Failure -> setError(outcome.error.message)
                is CommandOutcome.Success -> {
                    val follow = command is Command.Session.Run || command is Command.Session.Open
                    outcome.afterReply?.invoke()
                    mutableState.value = mutableState.value.copy(error = null)
                    if (follow) {
                        mutableEvents.tryEmit(ProfilesEvent.FollowSession)
                    } else {
                        refresh()
                    }
                }
            }
        }
    }

    private fun submit(command: Command, onDone: (CommandOutcome) -> Unit) {
        core.submit(command, onDone)
    }

    private fun navigate(screen: ProfileScreen) {
        mutableState.value = mutableState.value.copy(screen = screen, error = null)
    }

    private fun setError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }
}
