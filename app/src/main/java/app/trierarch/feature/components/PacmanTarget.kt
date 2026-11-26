package app.trierarch.feature.components

import java.io.File

/**
 * A concrete environment that can run pacman. UI code selects one of these,
 * and PacmanTransaction stays unaware of PRoot/chroot/DroidSpaces differences.
 */
sealed interface PacmanTarget {
    val displayName: String
    fun environment(): PacmanEnvironment

    data class Proot(
        override val displayName: String,
        val rootfs: File,
        val nativeLibraryDirectory: File,
        val cacheDirectory: File,
    ) : PacmanTarget {
        override fun environment(): PacmanEnvironment =
            ProotPacmanEnvironment(rootfs, nativeLibraryDirectory, cacheDirectory)
    }

    data class Chroot(
        override val displayName: String,
        val rootfs: File,
    ) : PacmanTarget {
        override fun environment(): PacmanEnvironment =
            ChrootPacmanEnvironment(rootfs)
    }

    data class DroidSpaces(
        override val displayName: String,
        val container: String,
        val cacheDirectory: File,
    ) : PacmanTarget {
        override fun environment(): PacmanEnvironment =
            DroidSpacesPacmanEnvironment(container, cacheDirectory)
    }
}
