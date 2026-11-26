package app.trierarch.ui.gui

import app.trierarch.feature.rootfs.InstalledRootfs

internal sealed interface ComponentsTarget {
    data class AppRootfs(val rootfs: InstalledRootfs) : ComponentsTarget
    data class DroidSpaces(val container: String) : ComponentsTarget
}
