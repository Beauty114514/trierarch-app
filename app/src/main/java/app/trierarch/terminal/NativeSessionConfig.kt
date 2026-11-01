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

/**
 * One ordered key/value config array for the JNI open calls.  Keys are parsed
 * by native code, so the bridge no longer depends on positional arguments.
 */
internal class JniSessionConfig {
    private val fields = linkedMapOf<String, String>()

    fun put(key: String, value: String) {
        check(fields.put(key, value) == null) { "duplicate JNI session key: $key" }
    }

    fun putList(prefix: String, values: List<String>) {
        put("$prefix.count", values.size.toString())
        values.forEachIndexed { index, value -> put("$prefix.$index", value) }
    }

    fun toArray(): Array<String> = fields.map { (key, value) -> "$key=$value" }.toTypedArray()
}

internal fun CommandSessionConfig.toJniConfig(): Array<String> = JniSessionConfig().apply {
    put("command", launchSpec.command)
    putList("arguments", launchSpec.arguments.toList())
    put("working_directory", launchSpec.workingDirectory.absolutePath)
    putList("environment", launchSpec.environment.toList())
}.toArray()

internal fun SessionEnvironment.addTo(config: JniSessionConfig) {
    config.put("x11_socket_directory", x11SocketDirectory.pathOrEmpty())
    config.put("wayland_runtime_directory", waylandRuntimeDirectory.pathOrEmpty())
    config.put("wayland_ime_bridge", waylandImeBridge.pathOrEmpty())
    config.put("virgl_runtime_directory", virglRuntimeDirectory.pathOrEmpty())
    config.put("pipewire_runtime_directory", pipewireRuntimeDirectory.pathOrEmpty())
    config.put("udev_compatibility_library", udevCompatibilityLibrary.pathOrEmpty())
}

internal fun ProotSessionConfig.toJniConfig(): Array<String> = JniSessionConfig().apply {
    put("rootfs", rootfs.absolutePath)
    put("shell", shell)
    put("native_library_directory", nativeLibraryDirectory.absolutePath)
    put("cache_directory", cacheDirectory.absolutePath)
    environment.addTo(this)
    putList("launch_argv", launchArgv)
    putList("graphics_environment", graphicsEnvironment)
}.toArray()

internal fun ChrootSessionConfig.toJniConfig(): Array<String> = JniSessionConfig().apply {
    put("rootfs", rootfs.path)
    put("shell", shell)
    environment.addTo(this)
    put("chroot_supervisor", chrootSupervisor.absolutePath)
    putList("launch_argv", launchArgv)
    putList("graphics_environment", graphicsEnvironment)
}.toArray()

internal fun DroidspacesSessionConfig.toJniConfig(): Array<String> = JniSessionConfig().apply {
    put("container", profile.container)
    put("user", profile.user)
    environment.addTo(this)
    put("droidspaces_supervisor", droidspacesSupervisor.absolutePath)
    putList("launch_argv", profile.launchArgv.orEmpty())
    putList("graphics_environment", profile.graphics.environment())
}.toArray()
