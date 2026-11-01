package app.trierarch.terminal

import app.trierarch.config.ProfileStore

/**
 * Host-side services whose lifetime is shared by every runtime session.
 *
 * The interface exists so the startup order can be tested without starting
 * Android controllers or a real PTY.
 */
interface RuntimeHosts {
    fun startVirgl(venus: Boolean): String
    fun startWayland(bufferMode: String)
    fun startPipeWire(): String
    fun stopWayland()
    fun stopVirgl()
    fun stopPipeWire()
    fun installGbmShim(waylandRuntimeDirectory: String)
}

/** Android-owned paths that are derived per display and compatibility mode. */
interface RuntimePaths {
    fun x11SocketDirectory(display: String): String?
    fun waylandRuntimeDirectory(display: String): String?
    fun waylandImeBridge(display: String): String?
    fun udevCompatibilityLibrary(enabled: Boolean): String
}

internal data class RuntimeLaunchSpec(
    val profileId: String,
    val display: String,
    val renderer: String,
    val bufferMode: String,
    val audioBackend: String,
    val enablesUdevMonitorShim: Boolean,
    val installGbmShim: Boolean,
)

internal data class RuntimeResources(
    val waylandRuntimeDirectory: String?,
    val virglRuntimeDirectory: String,
    val pipewireRuntimeDirectory: String,
    val x11SocketDirectory: String? = null,
    val waylandImeBridge: String? = null,
    val udevCompatibilityLibrary: String = "",
)

/**
 * One place for the host-service lifecycle shared by PRoot, chroot and
 * DroidSpaces.  `prepareHosts` intentionally runs before the session factory
 * try/catch, matching the existing cleanup semantics.
 */
class RuntimeBootstrap(
    private val hosts: RuntimeHosts,
    private val paths: RuntimePaths,
) {
    internal fun prepareHosts(spec: RuntimeLaunchSpec): RuntimeResources {
        val virglRuntimeDirectory = if (usesVtestTransport(spec.renderer)) {
            hosts.startVirgl(spec.renderer == ProfileStore.GRAPHICS_VENUS)
        } else {
            ""
        }
        if (spec.display == ProfileStore.DISPLAY_WAYLAND) {
            hosts.startWayland(spec.bufferMode)
        }
        val pipewireRuntimeDirectory = if (spec.audioBackend == ProfileStore.AUDIO_PIPEWIRE) {
            hosts.startPipeWire()
        } else {
            ""
        }
        val waylandRuntime = paths.waylandRuntimeDirectory(spec.display)
        if (spec.installGbmShim &&
            waylandRuntime != null &&
            spec.renderer == ProfileStore.GRAPHICS_VIRGL &&
            spec.bufferMode == ProfileStore.BUFFER_DMABUF
        ) {
            hosts.installGbmShim(waylandRuntime)
        }
        return RuntimeResources(
            waylandRuntimeDirectory = waylandRuntime,
            virglRuntimeDirectory = virglRuntimeDirectory,
            pipewireRuntimeDirectory = pipewireRuntimeDirectory,
        )
    }

    internal fun finishResources(
        spec: RuntimeLaunchSpec,
        resources: RuntimeResources,
    ): RuntimeResources = resources.copy(
        x11SocketDirectory = paths.x11SocketDirectory(spec.display),
        waylandImeBridge = paths.waylandImeBridge(spec.display),
        udevCompatibilityLibrary = paths.udevCompatibilityLibrary(spec.enablesUdevMonitorShim),
    )

    internal fun stopWayland() = hosts.stopWayland()

    internal fun stopVirgl() = hosts.stopVirgl()

    internal fun stopPipeWire() = hosts.stopPipeWire()

    private fun usesVtestTransport(renderer: String): Boolean =
        renderer == ProfileStore.GRAPHICS_VIRGL || renderer == ProfileStore.GRAPHICS_VENUS
}
