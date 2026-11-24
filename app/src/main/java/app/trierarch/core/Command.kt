package app.trierarch.core

/** Operations both frontends can request from the core. */
sealed interface Command {
    /** Session lifecycle and presentation commands. */
    sealed interface Session : Command {
        data class Run(val id: String) : Session
        data class Open(val id: String) : Session
        data class Stop(val id: String?) : Session

        /** Presentation only: brings the in-app GUI forward without touching a session. */
        data object OpenGui : Session
    }

    /** Profile document mutations; reads are [Core] queries. */
    sealed interface Document : Command {
        data class Create(val id: String) : Document
        data class Save(val id: String, val text: String, val expectedRevision: String) : Document
        data class Delete(val id: String, val expectedRevision: String) : Document
    }
}
