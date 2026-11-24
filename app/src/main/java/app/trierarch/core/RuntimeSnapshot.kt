package app.trierarch.core

/** Lifecycle phase the GUI shows for one profile. */
enum class SessionPhase {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
}

/** One running or pending session; stopped profiles have no entry. */
data class SessionSnapshot(
    val id: String,
    val phase: SessionPhase,
    val active: Boolean,
)

/** A read-only view of the runtime sessions for the GUI. */
data class RuntimeSnapshot(
    val sessions: Map<String, SessionSnapshot>,
    val pending: String?,
)

/**
 * Pure mapping from the controller's state, kept separate from Android so it
 * can be unit tested. [active] is the profile the terminal side shows, if any.
 */
internal fun buildRuntimeSnapshot(
    running: Set<String>,
    active: String?,
    pendingId: String?,
    pendingStopping: Boolean,
): RuntimeSnapshot {
    val pendingPhase = if (pendingStopping) SessionPhase.STOPPING else SessionPhase.STARTING
    val sessions = buildMap {
        running.forEach { id ->
            put(
                id,
                SessionSnapshot(
                    id = id,
                    phase = if (pendingId == id) pendingPhase else SessionPhase.RUNNING,
                    active = active == id,
                ),
            )
        }
        if (pendingId != null && pendingId !in running) {
            put(pendingId, SessionSnapshot(pendingId, pendingPhase, active == pendingId))
        }
    }
    return RuntimeSnapshot(sessions, pendingId)
}
