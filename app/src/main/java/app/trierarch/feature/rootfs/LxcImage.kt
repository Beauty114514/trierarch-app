package app.trierarch.feature.rootfs

/** One rootfs tarball offered by the Linux Containers image server. */
data class LxcImage(
    val product: String,
    val os: String,
    val release: String,
    val variant: String,
    val arch: String,
    val buildDate: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
) {
    val title: String
        get() = listOf(os, release)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { product }

    /** The docs recommend Arch, but every distro stays selectable. */
    val recommended: Boolean
        get() = os.equals("Archlinux", ignoreCase = true)

    /** Deterministic archive name; native import names use a stricter rule later. */
    val fileName: String
        get() = buildString {
            append(sanitize(os))
            append('-')
            append(sanitize(release))
            append('-')
            append(buildDate.substringBefore('_').filter(Char::isDigit).ifEmpty { "unknown" })
            append('-')
            append(sanitize(arch))
            append(".tar.xz")
        }

    private fun sanitize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .ifEmpty { "rootfs" }
}
