package app.trierarch.terminal

import app.trierarch.config.ProfileStore
import app.trierarch.runtime.TerminalLaunchSpec
import java.io.File

/** Filesystem-backed services shared by the PRoot, chroot and DroidSpaces sessions. */
data class SessionEnvironment(
    val x11SocketDirectory: File? = null,
    val waylandRuntimeDirectory: File? = null,
    val waylandImeBridge: File? = null,
    val virglRuntimeDirectory: File? = null,
    val pipewireRuntimeDirectory: File? = null,
    val udevCompatibilityLibrary: File? = null,
)

data class CommandSessionConfig(
    val launchSpec: TerminalLaunchSpec,
)

data class ProotSessionConfig(
    val rootfs: File,
    val shell: String,
    val nativeLibraryDirectory: File,
    val cacheDirectory: File,
    val environment: SessionEnvironment,
    val launchArgv: List<String>,
    val graphicsEnvironment: List<String>,
)

data class ChrootSessionConfig(
    val rootfs: File,
    val shell: String,
    val environment: SessionEnvironment,
    val chrootSupervisor: File,
    val launchArgv: List<String>,
    val graphicsEnvironment: List<String>,
    val diagnosticLog: File?,
)

data class DroidspacesSessionConfig(
    val profile: ProfileStore.DroidspacesProfile,
    val environment: SessionEnvironment,
    val droidspacesSupervisor: File,
)

internal fun String?.toFileOrNull(): File? =
    this?.takeIf { it.isNotEmpty() }?.let(::File)

internal fun File?.pathOrEmpty(): String = this?.absolutePath.orEmpty()

internal fun RuntimeResources.toSessionEnvironment(): SessionEnvironment = SessionEnvironment(
    x11SocketDirectory = x11SocketDirectory.toFileOrNull(),
    waylandRuntimeDirectory = waylandRuntimeDirectory.toFileOrNull(),
    waylandImeBridge = waylandImeBridge.toFileOrNull(),
    virglRuntimeDirectory = virglRuntimeDirectory.toFileOrNull(),
    pipewireRuntimeDirectory = pipewireRuntimeDirectory.toFileOrNull(),
    udevCompatibilityLibrary = udevCompatibilityLibrary.toFileOrNull(),
)
