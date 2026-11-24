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

    fun read(id: String): DocumentContent? = profiles.readDocument(id)

    fun sessions(): RuntimeSnapshot = sessionService.snapshot()

    fun status(id: String?): CommandOutcome = sessionService.status(id)

    fun submit(command: Command, onDone: (CommandOutcome) -> Unit) {
        when (command) {
            is Command.Session -> sessionService.dispatch(command, onDone)
            is Command.Document -> onDone(apply(command))
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
        }
    } catch (error: ProfileServiceException) {
        CommandOutcome.Failure(error.error)
    } catch (error: Exception) {
        CommandOutcome.Failure(CommandError.Failed(error.message ?: "document operation failed"))
    }
}
