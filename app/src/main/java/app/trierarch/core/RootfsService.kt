package app.trierarch.core

import app.trierarch.nativebridge.NativePtyBridge
import java.io.File

/** Imports/removes PRoot rootfs trees; extraction itself lives in native code. */
class RootfsService(private val filesDirectory: File) {
    fun import(archivePath: String, name: String): String =
        NativePtyBridge.importRootfs(archivePath, filesDirectory.absolutePath, name)

    fun remove(name: String): String =
        NativePtyBridge.removeRootfs(filesDirectory.absolutePath, name)
}
