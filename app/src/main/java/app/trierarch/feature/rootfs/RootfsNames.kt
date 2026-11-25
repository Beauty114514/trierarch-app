package app.trierarch.feature.rootfs

/**
 * Mirrors the native importer's name rule so the GUI can reject bad names
 * before starting a long extraction.
 */
internal fun validateRootfsName(name: String): String? {
    if (name.isEmpty()) return "rootfs name is empty"
    if (name.length > 64) return "rootfs name must be at most 64 characters"
    if (name[0] !in 'a'..'z') {
        return "rootfs name must start with a lowercase letter"
    }
    if (!name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) {
        return "rootfs name must use lowercase letters, digits, '-' or '_'"
    }
    return null
}
