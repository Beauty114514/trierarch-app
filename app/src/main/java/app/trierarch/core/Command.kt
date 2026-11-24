package app.trierarch.core

/** Operations both frontends can request from the core. */
sealed interface Command {
    data class Run(val id: String) : Command
    data class Open(val id: String) : Command
    data class Stop(val id: String?) : Command

    /** Presentation only: brings the in-app GUI forward without touching a session. */
    data object OpenGui : Command
}
