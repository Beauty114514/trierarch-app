package app.trierarch.core

/** The result of one command; both frontends consume it without interpreting strings. */
sealed interface CommandOutcome {
    data class Success(val message: String, val afterReply: (() -> Unit)? = null) : CommandOutcome
    data class Failure(val error: CommandError) : CommandOutcome
}
