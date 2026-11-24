package app.trierarch.core

import java.io.File

/**
 * A read-only summary of one profile document for the GUI's environment list.
 *
 * [id] is the file name: it is the identity `--run`, `--stop` and the runtime
 * controller resolve. [error] carries the validation text the CLI would print.
 */
data class ProfileDocument(
    val file: File,
    val id: String,
    val runtime: String,
    val display: String,
    val renderer: String?,
    val error: String?,
)
