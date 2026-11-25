package app.trierarch.feature.profiles

/** The fields needed to create one profile; each runtime has its own shape. */
sealed interface ProfileDraft {
    val id: String
    val display: String

    data class Proot(
        override val id: String,
        val rootfs: String,
        val shell: String,
        override val display: String = DISPLAY_TERMINAL,
    ) : ProfileDraft

    data class Chroot(
        override val id: String,
        val rootfs: String,
        val shell: String,
        override val display: String = DISPLAY_TERMINAL,
    ) : ProfileDraft

    data class Droidspaces(
        override val id: String,
        val container: String,
        val user: String,
        override val display: String = DISPLAY_TERMINAL,
    ) : ProfileDraft

    companion object {
        const val DISPLAY_TERMINAL = "none"
        const val DISPLAY_X11 = "x11"
        const val DISPLAY_WAYLAND = "wayland"
    }
}

internal val ProfileDraft.runtime: String
    get() = when (this) {
        is ProfileDraft.Proot -> "proot"
        is ProfileDraft.Chroot -> "chroot"
        is ProfileDraft.Droidspaces -> "droidspaces"
    }
