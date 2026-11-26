package app.trierarch.feature.components

import app.trierarch.nativebridge.NativePtyBridge
import java.io.File

/**
 * Runs pacman inside a chroot rootfs through the device's su provider. App
 * rootfs directories are staged directly; the privileged runner performs the
 * chroot and command execution.
 */
class ChrootPacmanEnvironment(private val rootfs: File) : PacmanEnvironment {
    override val displayName: String = "chroot"

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
            add("launch_argv.count=${command.size}")
            command.forEachIndexed { index, argument -> add("launch_argv.$index=$argument") }
        }
        val raw = NativePtyBridge.runChrootCommand(config.toTypedArray())
        val newline = raw.indexOf('\n')
        require(newline > 0) { "invalid chroot command result" }
        val exitCode = raw.substring(0, newline).toIntOrNull()
            ?: error("invalid chroot exit code")
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
