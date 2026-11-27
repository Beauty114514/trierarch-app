package app.trierarch.feature.components

import java.io.File

/**
 * A concrete environment that can run pacman. UI code selects one of these,
 * and PacmanTransaction stays unaware of PRoot/chroot/DroidSpaces differences.
 */
sealed interface PacmanTarget {
    val displayName: String
    val id: String
    fun environment(): PacmanEnvironment

    data class Proot(
        override val displayName: String,
        val rootfs: File,
        val nativeLibraryDirectory: File,
        val cacheDirectory: File,
    ) : PacmanTarget {
        override val id: String = "proot:${rootfs.absolutePath}"
        override fun environment(): PacmanEnvironment =
            ProotPacmanEnvironment(rootfs, nativeLibraryDirectory, cacheDirectory)
    }

    data class Chroot(
        override val displayName: String,
        val rootfs: File,
    ) : PacmanTarget {
        override val id: String = "chroot:${rootfs.absolutePath}"
        override fun environment(): PacmanEnvironment =
            ChrootPacmanEnvironment(rootfs)
    }

    data class DroidSpaces(
        override val displayName: String,
        val container: String,
        val cacheDirectory: File,
    ) : PacmanTarget {
        override val id: String = "droidspaces:$container"
        override fun environment(): PacmanEnvironment =
            DroidSpacesPacmanEnvironment(container, cacheDirectory)
    }
}
