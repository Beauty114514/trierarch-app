package app.trierarch.feature.components

import app.trierarch.nativebridge.NativePtyBridge
import java.io.File

/**
 * Runs pacman inside a DroidSpaces container. Packages are staged in app
 * storage and exposed to a stopped container with a temporary bind; a running
 * container cannot receive a new bind, so the caller must stop it first.
 */
class DroidSpacesPacmanEnvironment(
    private val container: String,
    private val cacheDirectory: File,
) : PacmanEnvironment {
    override val displayName: String = "DroidSpaces"

    private var stagingHost: File? = null
    private var stagingGuest: String? = null

    override fun stagePackages(packageDirectory: File): String {
        val name = "trierarch-components-${System.nanoTime()}"
        val host = File(cacheDirectory, name)
        require(host.mkdirs()) { "cannot create ${host.path}" }
        packageDirectory.listFiles().orEmpty().forEach { packageFile ->
            require(packageFile.isFile) { "not a package file: ${packageFile.name}" }
            packageFile.copyTo(File(host, packageFile.name), overwrite = false)
        }
        stagingHost = host
        stagingGuest = "/var/tmp/$name"
        return stagingGuest!!
    }

    override fun runPacman(arguments: List<String>): PacmanResult {
        require(arguments.isNotEmpty()) { "pacman command is empty" }
        val command = listOf("/usr/bin/pacman") + arguments.drop(1)
        val config = buildList {
            add("container=$container")
            add("launch_argv.count=${command.size}")
            command.forEachIndexed { index, argument -> add("launch_argv.$index=$argument") }
            add("stop_after=true")
            stagingHost?.let { host ->
                add("bind_host=${host.absolutePath}")
                add("bind_guest=${stagingGuest.orEmpty()}")
            }
        }
        val raw = NativePtyBridge.runDroidspacesCommand(config.toTypedArray())
        val newline = raw.indexOf('\n')
        require(newline > 0) { "invalid DroidSpaces command result" }
        val exitCode = raw.substring(0, newline).toIntOrNull()
            ?: error("invalid DroidSpaces exit code")
        return PacmanResult(
            command = command.joinToString(" "),
            output = raw.substring(newline + 1),
            exitCode = exitCode,
        )
    }

    override fun cleanup(stagingDirectory: String) {
        stagingHost?.deleteRecursively()
        stagingHost = null
        stagingGuest = null
    }
}
