package app.trierarch.feature.components

import app.trierarch.nativebridge.NativePtyBridge
import java.io.File

/**
 * Runs pacman inside a PRoot rootfs. Package files are copied into the rootfs
 * so pacman's scripts and the package manager see ordinary local paths.
 */
class ProotPacmanEnvironment(
    private val rootfs: File,
    private val nativeLibraryDirectory: File,
    private val cacheDirectory: File,
) : PacmanEnvironment {
    override val displayName: String = "PRoot"

    override fun stagePackages(packageDirectory: File): String {
        val name = "trierarch-components-${System.nanoTime()}"
        val guestDirectory = "/var/tmp/$name"
        val hostDirectory = File(rootfs, "var/tmp/$name")
        require(hostDirectory.mkdirs()) { "cannot create ${hostDirectory.path}" }
        packageDirectory.listFiles().orEmpty().forEach { packageFile ->
            require(packageFile.isFile) { "not a package file: ${packageFile.name}" }
            packageFile.copyTo(File(hostDirectory, packageFile.name), overwrite = false)
        }
        return guestDirectory
    }

    override fun runPacman(arguments: List<String>): PacmanResult {
        require(arguments.isNotEmpty()) { "pacman command is empty" }
        val command = listOf("/usr/bin/pacman") + arguments.drop(1)
        val config = buildList {
            add("rootfs=${rootfs.absolutePath}")
            add("shell=/bin/sh")
            add("native_library_directory=${nativeLibraryDirectory.absolutePath}")
            add("cache_directory=${cacheDirectory.absolutePath}")
            add("launch_argv.count=${command.size}")
            command.forEachIndexed { index, argument -> add("launch_argv.$index=$argument") }
        }
        val raw = NativePtyBridge.runProotCommand(config.toTypedArray())
        val newline = raw.indexOf('\n')
        require(newline > 0) { "invalid PRoot command result" }
        val exitCode = raw.substring(0, newline).toIntOrNull()
            ?: error("invalid PRoot exit code")
        return PacmanResult(
            command = command.joinToString(" "),
            output = raw.substring(newline + 1),
            exitCode = exitCode,
        )
    }

    override fun cleanup(stagingDirectory: String) {
        val relative = stagingDirectory.removePrefix("/")
        File(rootfs, relative).deleteRecursively()
    }
}
