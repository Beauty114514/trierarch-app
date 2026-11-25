package app.trierarch.core

/**
 * The slice of [Core] the GUI feature layer needs. Keeping it an interface lets
 * controllers be tested with a fake core, without Android or real files.
 */
interface CoreApi {
    fun documents(): List<ProfileDocument>
    fun read(id: String): DocumentContent?
    fun sessions(): RuntimeSnapshot
    fun logs(id: String, lines: Int): String?
    fun submit(command: Command, onDone: (CommandOutcome) -> Unit)
    fun addListener(listener: (CoreEvent) -> Unit)
    fun removeListener(listener: (CoreEvent) -> Unit)
}
