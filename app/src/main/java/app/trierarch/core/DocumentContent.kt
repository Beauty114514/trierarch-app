package app.trierarch.core

/** The editable content of one profile document. */
data class DocumentContent(
    val id: String,
    val text: String,
    val revision: String,
)

/** A document operation failed with a typed core error. */
class ProfileServiceException(val error: CommandError) : Exception(error.message)
