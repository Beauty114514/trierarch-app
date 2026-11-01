package app.trierarch.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.trierarch.config.ProfileStore
import app.trierarch.runtime.InternalShellLaunchSpec
import app.trierarch.wayland.WaylandImeBridgeRuntime
import java.io.File

/** Owns the built-in app-internal shell independently of a terminal view instance. */
class DefaultTerminalViewModel @JvmOverloads constructor(
    application: Application,
    private val runtimeBootstrap: RuntimeBootstrap = RuntimeBootstrap(
        AndroidRuntimeHosts(application),
        AndroidRuntimePaths(application),
    ),
) : AndroidViewModel(application) {
    private val app = application
    private var internalSession: NativePtySession = createInternalShell()
    private val runtimeSessions = linkedMapOf<String, RuntimeSession>()
    private var displayedProfileId: String? = null

    /** The PTY currently displayed by the terminal surface. */
    var session: NativePtySession = internalSession
        private set

    fun restartInternalShell() {
        stopRuntime()
        session = usableInternalSession()
    }

    /** Shows the persistent management shell without stopping a profile runtime. */
    fun showInternalShell() {
        session = usableInternalSession()
        displayedProfileId = null
    }

    fun reportRuntimeLaunchFailure(profileId: String, error: Throwable) {
        showInternalShell()
        internalSession.appendStatus(
            "\r\n[Trierarch failed to start $profileId: ${error.message ?: error.javaClass.simpleName}]\r\n" +
                "Session diagnostics: ${sessionDiagnosticLog(profileId).absolutePath}\r\n",
        )
    }

    fun restartProot(profile: ProfileStore.ProotProfile) {
        launchRuntime(
            RuntimeLaunchSpec(
                profileId = profile.id,
                display = profile.display,
                renderer = profile.graphics.renderer,
                bufferMode = profile.graphics.resolvedBuffer,
                audioBackend = profile.audioBackend,
                enablesUdevMonitorShim = profile.compatibility.enablesUdevMonitorShim,
                installGbmShim = true,
            ),
        ) { resources ->
            NativePtySession(
                rootfsDirectory = profile.rootfs,
                shell = profile.shell,
                nativeLibraryDirectory = File(app.applicationInfo.nativeLibraryDir),
                cacheDirectory = app.cacheDir,
                x11SocketDirectory = resources.x11SocketDirectory,
                waylandRuntimeDirectory = resources.waylandRuntimeDirectory,
                waylandImeBridge = resources.waylandImeBridge,
                virglRuntimeDirectory = resources.virglRuntimeDirectory,
                pipewireRuntimeDirectory = resources.pipewireRuntimeDirectory,
                udevCompatibilityLibrary = resources.udevCompatibilityLibrary,
                launchArgv = profile.launchArgv.orEmpty().toTypedArray(),
                graphicsEnvironment = profile.graphics.environment().toTypedArray(),
                clipboard = AndroidTerminalClipboard(app),
            )
        }
    }

    fun restartChroot(profile: ProfileStore.ChrootProfile) {
        launchRuntime(
            RuntimeLaunchSpec(
                profileId = profile.id,
                display = profile.display,
                renderer = profile.graphics.renderer,
                bufferMode = profile.graphics.resolvedBuffer,
                audioBackend = profile.audioBackend,
                enablesUdevMonitorShim = profile.compatibility.enablesUdevMonitorShim,
                installGbmShim = false,
            ),
        ) { resources ->
            NativePtySession(
                chrootRootfs = profile.rootfs,
                shell = profile.shell,
                x11SocketDirectory = resources.x11SocketDirectory,
                waylandRuntimeDirectory = resources.waylandRuntimeDirectory,
                waylandImeBridge = resources.waylandImeBridge,
                chrootSupervisor = WaylandImeBridgeRuntime.supervisor(app).absolutePath,
                virglRuntimeDirectory = resources.virglRuntimeDirectory,
                pipewireRuntimeDirectory = resources.pipewireRuntimeDirectory,
                launchArgv = profile.launchArgv.orEmpty().toTypedArray(),
                graphicsEnvironment = profile.graphics.environment().toTypedArray(),
                udevCompatibilityLibrary = resources.udevCompatibilityLibrary,
                diagnosticLog = sessionDiagnosticLog(profile.id),
                clipboard = AndroidTerminalClipboard(app),
            )
        }
    }

    fun restartDroidspaces(profile: ProfileStore.DroidspacesProfile) {
        launchRuntime(
            RuntimeLaunchSpec(
                profileId = profile.id,
                display = profile.display,
                renderer = profile.graphics.renderer,
                bufferMode = profile.graphics.resolvedBuffer,
                audioBackend = profile.audioBackend,
                enablesUdevMonitorShim = profile.compatibility.enablesUdevMonitorShim,
                installGbmShim = true,
            ),
        ) { resources ->
            NativePtySession(
                droidspacesProfile = profile,
                x11SocketDirectory = resources.x11SocketDirectory,
                waylandRuntimeDirectory = resources.waylandRuntimeDirectory,
                waylandImeBridge = resources.waylandImeBridge,
                droidspacesSupervisor = WaylandImeBridgeRuntime.droidspacesSupervisor(app).absolutePath,
                virglRuntimeDirectory = resources.virglRuntimeDirectory,
                pipewireRuntimeDirectory = resources.pipewireRuntimeDirectory,
                udevCompatibilityLibrary = resources.udevCompatibilityLibrary,
                clipboard = AndroidTerminalClipboard(app),
            )
        }
    }

    fun isRuntimeRunning(): Boolean = runningProfileIds().isNotEmpty()

    /** The profile currently attached to Trierarch's foreground terminal surface. */
    fun activeProfileId(): String? = displayedProfileId?.takeIf { isProfileRunning(it) }

    fun isProfileRunning(id: String): Boolean = runtimeSessions[id]?.session?.isRunning() == true

    fun runningProfileIds(): Set<String> = runtimeSessions
        .filterValues { it.session.isRunning() }
        .keys

    /** Makes an already-running session visible without changing its lifecycle. */
    fun showRuntime(id: String): Boolean {
        val runtime = runtimeSessions[id] ?: return false
        if (!runtime.session.isRunning()) return false
        displayedProfileId = id
        session = runtime.session
        return true
    }

    /** Stops one Trierarch-owned profile session, or every session when no id is supplied. */
    fun stopRuntime(id: String? = null) {
        if (id == null) {
            runtimeSessions.keys.toList().forEach(::closeRuntime)
        } else {
            closeRuntime(id)
        }
        session = usableInternalSession()
    }

    override fun onCleared() {
        stopRuntime()
        internalSession.close()
    }

    private fun createInternalShell() = NativePtySession(
        launchSpec = InternalShellLaunchSpec.create(
            filesDirectory = app.filesDir,
            cacheDirectory = app.cacheDir,
            nativeLibraryDirectory = File(app.applicationInfo.nativeLibraryDir),
        ),
        clipboard = AndroidTerminalClipboard(app),
    )

    private fun usableInternalSession(): NativePtySession {
        if (!internalSession.isRunning()) internalSession = createInternalShell()
        return internalSession
    }

    private fun closeRuntime(id: String) {
        runtimeSessions.remove(id)?.session?.close()
        if (displayedProfileId == id) displayedProfileId = null
        if (runtimeSessions.values.none { it.display == ProfileStore.DISPLAY_WAYLAND }) {
            runtimeBootstrap.stopWayland()
        }
        if (runtimeSessions.values.none { usesVtestTransport(it.renderer) }) {
            runtimeBootstrap.stopVirgl()
        }
        if (runtimeSessions.values.none { it.pipewire }) {
            runtimeBootstrap.stopPipeWire()
        }
    }

    private fun launchRuntime(
        spec: RuntimeLaunchSpec,
        createSession: (RuntimeResources) -> NativePtySession,
    ) {
        closeRuntime(spec.profileId)
        val resources = runtimeBootstrap.prepareHosts(spec)
        val next = try {
            val sessionResources = runtimeBootstrap.finishResources(spec, resources)
            createSession(sessionResources).also { session ->
                if (spec.display == ProfileStore.DISPLAY_X11 ||
                    spec.display == ProfileStore.DISPLAY_WAYLAND
                ) {
                    session.start()
                }
            }
        } catch (error: Throwable) {
            if (runtimeSessions.values.none { it.pipewire }) runtimeBootstrap.stopPipeWire()
            throw error
        }
        runtimeSessions[spec.profileId] = RuntimeSession(
            next,
            spec.display,
            spec.renderer,
            spec.audioBackend == ProfileStore.AUDIO_PIPEWIRE,
        )
        displayedProfileId = spec.profileId
        session = next
    }

    private data class RuntimeSession(
        val session: NativePtySession,
        val display: String,
        val renderer: String,
        val pipewire: Boolean = false,
    )

    private fun usesVtestTransport(renderer: String): Boolean =
        renderer == ProfileStore.GRAPHICS_VIRGL || renderer == ProfileStore.GRAPHICS_VENUS

    private fun sessionDiagnosticLog(profileId: String): File =
        File(app.filesDir, "logs/sessions/$profileId.log")
}
