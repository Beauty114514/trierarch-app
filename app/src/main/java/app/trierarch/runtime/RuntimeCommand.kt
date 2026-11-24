package app.trierarch.runtime

/** Commands accepted from Trierarch's app-private internal CLI. */
sealed interface RuntimeCommand {
    data class Status(val id: String?) : RuntimeCommand
    data class Run(val id: String) : RuntimeCommand
    data class Stop(val id: String?) : RuntimeCommand

    /** Shows an already-running session; never starts, stops or restarts it. */
    data class OpenSession(val id: String) : RuntimeCommand

    /** Presentation only: brings the in-app GUI forward without touching a session. */
    data object OpenGui : RuntimeCommand
}

data class RuntimeCommandResult(
    val success: Boolean,
    val message: String,
    val afterReply: (() -> Unit)? = null,
)
