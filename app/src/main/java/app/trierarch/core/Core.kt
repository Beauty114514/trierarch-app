package app.trierarch.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * The single entry point both frontends use: the GUI in-process, the CLI
 * through the control socket. The core owns documents and sessions; frontends
 * only present them.
 */
class Core(
    private val profiles: ProfileService,
    private val sessionService: SessionService,
    private val rootfs: RootfsService,
) {
    private val listeners = CopyOnWriteArrayList<(CoreEvent) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val rootfsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "trierarch-rootfs").apply { isDaemon = true }
    }

    init {
        sessionService.setSessionsChangedListener {
            notify(CoreEvent.SessionsChanged)
        }
    }

    fun addListener(listener: (CoreEvent) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (CoreEvent) -> Unit) {
        listeners -= listener
    }

    fun documents(): List<ProfileDocument> = profiles.listDocuments()

    fun read(id: String): DocumentContent? = profiles.readDocument(id)

    fun sessions(): RuntimeSnapshot = sessionService.snapshot()

    fun logs(id: String, lines: Int): String? = sessionService.logs(id, lines)

    fun status(id: String?): CommandOutcome = sessionService.status(id)

    fun submit(command: Command, onDone: (CommandOutcome) -> Unit) {
        when (command) {
            is Command.Session -> sessionService.dispatch(command, onDone)
            is Command.Document -> {
                if (command.isRootfs()) {
                    // Extraction can take minutes; keep the UI and the socket
                    // handler's main-thread wait out of it.
                    rootfsExecutor.execute {
                        val outcome = apply(command)
                        main.post {
                            onDone(outcome)
                            if (outcome is CommandOutcome.Success) {
                                notify(CoreEvent.DocumentsChanged)
                            }
                        }
                    }
                } else {
                    val outcome = apply(command)
                    onDone(outcome)
                    if (outcome is CommandOutcome.Success) notify(CoreEvent.DocumentsChanged)
                }
            }
        }
    }

    private fun Command.Document.isRootfs(): Boolean =
        this is Command.Document.Import || this is Command.Document.Remove

    private fun notify(event: CoreEvent) {
        listeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }

    private fun apply(command: Command.Document): CommandOutcome = try {
        when (command) {
            is Command.Document.Create ->
                CommandOutcome.Success("Created profile: ${profiles.create(command.id)}")
            is Command.Document.Save ->
                CommandOutcome.Success(
                    "Saved profile: " +
                        profiles.save(command.id, command.text, command.expectedRevision),
                )
            is Command.Document.Delete -> {
                profiles.delete(command.id, command.expectedRevision)
                CommandOutcome.Success("Deleted profile: ${command.id}")
            }
            is Command.Document.Import -> CommandOutcome.Success(
                "Imported PRoot environment: " +
                    rootfs.import(command.archivePath, command.name),
            )
            is Command.Document.Remove -> {
                rootfs.remove(command.name)
                CommandOutcome.Success("Removed PRoot environment: ${command.name}")
            }
        }
    } catch (error: ProfileServiceException) {
        CommandOutcome.Failure(error.error)
    } catch (error: Exception) {
        CommandOutcome.Failure(CommandError.Failed(error.message ?: "document operation failed"))
    }
}
