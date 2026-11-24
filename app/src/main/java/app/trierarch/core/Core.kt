package app.trierarch.core

/**
 * The single entry point both frontends use: the GUI in-process, the CLI
 * through the control socket. The core owns documents and sessions; frontends
 * only present them.
 */
class Core(
    private val profiles: ProfileService,
    private val sessionService: SessionService,
) {
    fun documents(): List<ProfileDocument> = profiles.listDocuments()

    fun sessions(): RuntimeSnapshot = sessionService.snapshot()

    fun status(id: String?): CommandOutcome = sessionService.status(id)

    fun submit(command: Command, onDone: (CommandOutcome) -> Unit) =
        sessionService.dispatch(command, onDone)
}
