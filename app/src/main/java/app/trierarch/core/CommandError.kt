package app.trierarch.core

/**
 * A typed failure from the core.
 *
 * [message] is for humans (the CLI prints it, the GUI shows it); [code] is the
 * stable, machine-readable form the CLI will map to exit codes.
 */
sealed interface CommandError {
    val code: String
    val message: String

    data class AlreadyRunning(val id: String) : CommandError {
        override val code = "already_running"
        override val message = "profile is already running: $id"
    }

    data class Busy(val pendingId: String?) : CommandError {
        override val code = "busy"
        override val message = "runtime action in progress: $pendingId"
    }

    data class InvalidDocument(val id: String) : CommandError {
        override val code = "invalid_document"
        override val message = "profile does not exist or is invalid: $id"
    }

    data class DisplayBusy(val display: String, val by: String) : CommandError {
        override val code = "display_busy"
        override val message = "another $display session is running: $by; stop it first"
    }

    data class NotRunning(val id: String, val useRunHint: Boolean = false) : CommandError {
        override val code = "not_running"
        override val message = "profile is not running: $id" + if (useRunHint) "; use --run $id" else ""
    }

    data object NoSession : CommandError {
        override val code = "no_session"
        override val message = "no runtime is running"
    }

    data class Ambiguous(val ids: List<String> = emptyList()) : CommandError {
        override val code = "ambiguous"
        override val message = "multiple profiles are running; specify an ID"
    }

    data class Failed(val reason: String) : CommandError {
        override val code = "failed"
        override val message = reason
    }
}
