package app.trierarch.feature.profiles

import java.io.File

private val PROFILE_ID = Regex("[a-z][a-z0-9-]{0,63}")

/** Returns the first validation error, or null when the draft can be created. */
internal fun validateProfileDraft(draft: ProfileDraft): String? {
    if (!PROFILE_ID.matches(draft.id)) {
        return "profile id must start with a lowercase letter and contain only lowercase letters, digits, and hyphens"
    }
    if (draft.display !in setOf(
            ProfileDraft.DISPLAY_TERMINAL,
            ProfileDraft.DISPLAY_X11,
            ProfileDraft.DISPLAY_WAYLAND,
        )
    ) {
        return "display type must be none, x11, or wayland"
    }
    return when (draft) {
        is ProfileDraft.Proot -> validateRootfs(draft.rootfs, draft.shell)
        is ProfileDraft.Chroot -> validateRootfs(draft.rootfs, draft.shell)
        is ProfileDraft.Droidspaces -> {
            if (draft.container.trim().isEmpty()) {
                "container is required"
            } else {
                null
            }
        }
    }
}

private fun validateRootfs(rootfs: String, shell: String): String? {
    if (!rootfs.startsWith('/')) return "rootfs must be an absolute path"
    val directory = File(rootfs)
    if (!directory.isDirectory) {
        return "rootfs is not an accessible directory: $rootfs"
    }
    if (!File(directory, "etc/os-release").isFile) {
        return "rootfs is missing etc/os-release: $rootfs"
    }
    if (!shell.startsWith('/') || shell.split('/').any { it == ".." }) {
        return "shell must be an absolute path inside the rootfs"
    }
    if (!File(directory, shell.removePrefix("/")).isFile) {
        return "configured shell does not exist in rootfs: $shell"
    }
    return null
}

internal fun ProfileDraft.toToml(): String = buildString {
    appendLine("id = ${toml(id)}")
    appendLine("runtime = ${toml(runtime)}")
    when (this@toToml) {
        is ProfileDraft.Proot -> {
            appendLine("rootfs = ${toml(rootfs)}")
            appendLine("shell = ${toml(shell)}")
        }
        is ProfileDraft.Chroot -> {
            appendLine("rootfs = ${toml(rootfs)}")
            appendLine("shell = ${toml(shell)}")
        }
        is ProfileDraft.Droidspaces -> {
            appendLine("container = ${toml(container.trim())}")
            appendLine("user = ${toml(user.trim().ifEmpty { "root" })}")
        }
    }
    appendLine()
    appendLine("[display]")
    appendLine("type = ${toml(display)}")
}

private fun toml(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
