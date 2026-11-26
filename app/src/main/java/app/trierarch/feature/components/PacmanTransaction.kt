package app.trierarch.feature.components

import app.trierarch.nativebridge.NativePtyBridge
import java.io.File

data class PacmanResult(val command: String, val output: String, val exitCode: Int) {
    val success: Boolean get() = exitCode == 0
}

/** A runtime-specific way to stage package files and run pacman inside a rootfs. */
interface PacmanEnvironment {
    val displayName: String

    /** Copies package files into the guest and returns the guest staging directory. */
    fun stagePackages(packageDirectory: File): String

    /** Runs pacman inside the guest. */
    fun runPacman(arguments: List<String>): PacmanResult

    fun cleanup(stagingDirectory: String)
}

/**
 * Shared pacman transaction logic. It only knows how to prepare packages,
 * build package-manager commands, and interpret their output. The runtime
 * adapter owns getting the packages and the command into PRoot/chroot/
 * DroidSpaces.
 */
class PacmanTransaction(
    private val environment: PacmanEnvironment,
    private val extract: (archive: File, destination: File) -> Unit = { archive, destination ->
        NativePtyBridge.extractComponentArchive(archive.absolutePath, destination.absolutePath)
    },
) {
    fun install(archive: File, packages: List<String>): PacmanResult {
        require(archive.isFile) { "package archive does not exist: ${archive.name}" }
        require(packages.isNotEmpty()) { "pacman component has no package list" }
        val packageDirectory = createTempDirectory("trierarch-pacman-")
        var staging: String? = null
        try {
            extract(archive, packageDirectory)
            val packageFiles = packageDirectory.walkTopDown()
                .filter { it.isFile && it.name.contains(".pkg.tar.") }
                .sortedBy { it.name }
                .toList()
            require(packageFiles.isNotEmpty()) { "archive contains no .pkg.tar.* files" }
            val flatDirectory = File(packageDirectory, "flat").apply { mkdirs() }
            packageFiles.forEach { packageFile ->
                packageFile.copyTo(File(flatDirectory, packageFile.name), overwrite = false)
            }
            staging = environment.stagePackages(flatDirectory)
            val guestPaths = packageFiles.map { "${staging}/${it.name}" }
            val command = listOf("pacman", "-U", "--noconfirm") + guestPaths
            val result = environment.runPacman(command)
            if (!result.success) {
                throw PacmanException(result)
            }
            return result
        } finally {
            staging?.let(environment::cleanup)
            packageDirectory.deleteRecursively()
        }
    }

    /** Returns package name to installed version for packages that are present. */
    fun query(packages: List<String>): Map<String, String> {
        if (packages.isEmpty()) return emptyMap()
        val result = environment.runPacman(listOf("pacman", "-Q") + packages)
        if (result.exitCode != 0 && result.output.isBlank()) return emptyMap()
        return result.output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2 && parts[0].isNotEmpty()) parts[0] to parts[1] else null
            }
            .toMap()
    }

    fun uninstall(packages: List<String>): PacmanResult {
        require(packages.isNotEmpty()) { "pacman component has no package list" }
        val result = environment.runPacman(
            listOf("pacman", "-R", "--noconfirm") + packages,
        )
        if (!result.success) throw PacmanException(result)
        return result
    }

    private fun createTempDirectory(prefix: String): File =
        kotlin.io.path.createTempDirectory(prefix).toFile()
}

class PacmanException(val result: PacmanResult) :
    IllegalStateException("pacman failed (${result.exitCode}): ${result.output.take(500)}")
