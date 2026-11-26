package app.trierarch.feature.components

import app.trierarch.nativebridge.NativePtyBridge
import java.io.File

data class FileComponentEntry(
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAt: Long,
)

/**
 * A deliberately small file-component store.
 *
 * Installed components are whichever files or directories the user put under
 * <rootfs>/opt/trierarch/components. There is no per-component manifest or
 * hardcoded path: installing is extracting/copying, listing is reading the
 * directory, and deleting is deleting the selected entry.
 */
class FileComponentStore(private val componentsDirectory: File) {
    fun rootfsComponents(rootfs: File): File =
        File(rootfs, "opt/trierarch/components")

    fun entries(rootfs: File): List<FileComponentEntry> {
        val root = rootfsComponents(rootfs)
        if (!root.isDirectory) return emptyList()
        return root.listFiles().orEmpty()
            .filter { it.name != INDEX_MARKER && !it.name.startsWith(".") }
            .map {
                FileComponentEntry(
                    name = it.name,
                    directory = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    modifiedAt = it.lastModified(),
                )
            }
            .sortedWith(compareBy({ !it.directory }, { it.name }))
    }

    /** Files in files/components that can be installed, without an ID index. */
    fun availableArchives(): List<File> {
        if (!componentsDirectory.isDirectory) return emptyList()
        return componentsDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.name != INDEX_MARKER && !it.name.startsWith(".") }
            .sortedBy { it.name }
    }

    fun install(rootfs: File, archive: File): List<String> {
        require(archive.isFile) { "component archive does not exist: ${archive.name}" }
        val root = rootfsComponents(rootfs)
        require(root.isDirectory || root.mkdirs()) { "cannot create ${root.path}" }
        if (archive.name.endsWith(".tar.zst") || archive.name.endsWith(".tar.xz")) {
            return installArchive(root, archive)
        }
        val destination = File(root, archive.name)
        require(!destination.exists()) {
            "delete the existing ${archive.name} before installing a new copy"
        }
        require(archive.copyTo(destination, overwrite = false).isFile) {
            "could not copy ${archive.name}"
        }
        return listOf(archive.name)
    }

    private fun installArchive(root: File, archive: File): List<String> {
        val name = archive.name
            .removeSuffix(".tar.zst")
            .removeSuffix(".tar.xz")
        val destination = File(root, name)
        require(!destination.exists()) {
            "delete the existing $name before installing a new copy"
        }
        val staging = File(root, ".install-${System.nanoTime()}")
        try {
            NativePtyBridge.extractComponentArchive(
                archivePath = archive.absolutePath,
                destinationPath = staging.absolutePath,
            )
            require(staging.listFiles().orEmpty().isNotEmpty()) {
                "archive did not contain any files"
            }
            require(staging.renameTo(destination)) {
                "could not move the extracted component into $name"
            }
            return listOf(name)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun delete(rootfs: File, name: String) {
        require(name.isNotEmpty() && !name.contains('/') && !name.contains('\\')) {
            "invalid component entry"
        }
        File(rootfsComponents(rootfs), name).deleteRecursively()
    }

    companion object {
        private const val INDEX_MARKER = "index.toml"
    }
}
