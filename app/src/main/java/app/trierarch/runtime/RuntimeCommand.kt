package app.trierarch.runtime

/** Commands accepted from Trierarch's app-private internal CLI. */
sealed interface RuntimeCommand {
    data class Status(val id: String?) : RuntimeCommand
    data class Run(val id: String) : RuntimeCommand
    data class Stop(val id: String?) : RuntimeCommand
    data class Rerun(val id: String) : RuntimeCommand
}

data class RuntimeCommandResult(
    val success: Boolean,
    val message: String,
    val afterReply: (() -> Unit)? = null,
)
