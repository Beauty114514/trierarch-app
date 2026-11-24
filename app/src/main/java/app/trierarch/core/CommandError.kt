package app.trierarch.core

/**
 * A typed failure from the core.
 *
 * [message] is for humans (the CLI prints it, the GUI shows it); [code] is the
 * stable, machine-readable form the CLI maps to exit codes.
 */
sealed interface CommandError {
    val code: String
    val message: String

    data class AlreadyRunning(val id: String) : CommandError {
        override val code = "already_running"
        override val message = "profile is already running: $id; use --open $id to show it"
    }

    data class Busy(val pendingId: String?) : CommandError {
        override val code = "busy"
        override val message = "runtime action in progress: $pendingId"
    }

    data class InvalidDocument(val id: String, val reason: String? = null) : CommandError {
        override val code = "invalid_document"
        override val message = if (reason.isNullOrBlank()) {
            "profile does not exist or is invalid: $id"
        } else {
            "profile is invalid: $id: $reason"
        }
    }

    data class AlreadyExists(val id: String) : CommandError {
        override val code = "already_exists"
        override val message = "profile already exists: $id"
    }

    data class NotFound(val id: String) : CommandError {
        override val code = "not_found"
        override val message = "profile does not exist: $id"
    }

    data class LogNotFound(val id: String) : CommandError {
        override val code = "not_found"
        override val message = "no session log for: $id"
    }

    data class RevisionMismatch(val id: String) : CommandError {
        override val code = "revision_mismatch"
        override val message = "profile changed on disk: $id"
    }

    data class SourceBusy(val source: String, val by: String) : CommandError {
        override val code = "source_busy"
        override val message = "$source is already in use by '$by'; stop '$by' first"
    }

    data class DisplayBusy(val display: String, val by: String) : CommandError {
        override val code = "display_busy"
        override val message = "the $display display is already in use by '$by'; stop '$by' first"
    }

    data class NotRunning(val id: String, val useRunHint: Boolean = false) : CommandError {
        override val code = "not_running"
        override val message = "profile is not running: $id" + if (useRunHint) "; use --run $id" else ""
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
