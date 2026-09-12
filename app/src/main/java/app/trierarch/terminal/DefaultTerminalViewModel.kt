package app.trierarch.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.trierarch.config.ProfileStore
import app.trierarch.compat.GuestCompatibilityRuntime
import app.trierarch.runtime.InternalShellLaunchSpec
import app.trierarch.x11.X11Runtime
import app.trierarch.wayland.WaylandBridge
import app.trierarch.wayland.WaylandImeBridgeRuntime
import app.trierarch.virgl.VirglHostController
import java.io.File

/** Owns the built-in app-internal shell independently of a terminal view instance. */
class DefaultTerminalViewModel(application: Application) : AndroidViewModel(application) {
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
    }

    fun restartProot(profile: ProfileStore.ProotProfile) {
        closeRuntime(profile.id)
        val virglRuntimeDirectory = if (profile.graphics.renderer == ProfileStore.GRAPHICS_VIRGL) {
            VirglHostController.start(app).absolutePath
        } else {
            ""
        }
        if (profile.display == ProfileStore.DISPLAY_WAYLAND) {
            check(WaylandBridge.start(app)) { "Unable to start Wayland host" }
        }
        val next = NativePtySession(
            rootfsDirectory = profile.rootfs,
            shell = profile.shell,
            nativeLibraryDirectory = File(app.applicationInfo.nativeLibraryDir),
            cacheDirectory = app.cacheDir,
            x11SocketDirectory = x11SocketDirectory(profile.display),
            waylandRuntimeDirectory = waylandRuntimeDirectory(profile.display),
            waylandImeBridge = waylandImeBridge(profile.display),
            virglRuntimeDirectory = virglRuntimeDirectory,
            udevCompatibilityLibrary = GuestCompatibilityRuntime.udevMonitorLibrary(
                app, profile.compatibility.enablesUdevMonitorShim,
            )?.absolutePath.orEmpty(),
            launchArgv = profile.launchArgv.orEmpty().toTypedArray(),
            graphicsEnvironment = profile.graphics.environment().toTypedArray(),
            clipboard = AndroidTerminalClipboard(app),
        ).also { next ->
            if (profile.display == ProfileStore.DISPLAY_X11 || profile.display == ProfileStore.DISPLAY_WAYLAND) {
                next.start()
            }
        }
        runtimeSessions[profile.id] = RuntimeSession(next, profile.display, profile.graphics.renderer)
        displayedProfileId = profile.id
        session = next
    }

    fun restartChroot(profile: ProfileStore.ChrootProfile) {
        closeRuntime(profile.id)
        val next = NativePtySession(
            chrootRootfs = profile.rootfs,
            shell = profile.shell,
            x11SocketDirectory = x11SocketDirectory(profile.display),
            waylandRuntimeDirectory = waylandRuntimeDirectory(profile.display),
            waylandImeBridge = waylandImeBridge(profile.display),
            launchArgv = profile.launchArgv.orEmpty().toTypedArray(),
            graphicsEnvironment = profile.graphics.environment().toTypedArray(),
            udevCompatibilityLibrary = GuestCompatibilityRuntime.udevMonitorLibrary(
                app, profile.compatibility.enablesUdevMonitorShim,
            )?.absolutePath.orEmpty(),
            clipboard = AndroidTerminalClipboard(app),
        ).also { next ->
            if (profile.display == ProfileStore.DISPLAY_X11 || profile.display == ProfileStore.DISPLAY_WAYLAND) {
                next.start()
            }
        }
        runtimeSessions[profile.id] = RuntimeSession(next, profile.display, profile.graphics.renderer)
        displayedProfileId = profile.id
        session = next
    }

    fun restartDroidspaces(profile: ProfileStore.DroidspacesProfile) {
        closeRuntime(profile.id)
        val virglRuntimeDirectory = if (profile.graphics.renderer == ProfileStore.GRAPHICS_VIRGL) {
            VirglHostController.start(app).absolutePath
        } else {
            ""
        }
        if (profile.display == ProfileStore.DISPLAY_WAYLAND) {
            check(WaylandBridge.start(app)) { "Unable to start Wayland host" }
        }
        val next = NativePtySession(
            droidspacesProfile = profile,
            x11SocketDirectory = x11SocketDirectory(profile.display),
            waylandRuntimeDirectory = waylandRuntimeDirectory(profile.display),
            waylandImeBridge = waylandImeBridge(profile.display),
            virglRuntimeDirectory = virglRuntimeDirectory,
            udevCompatibilityLibrary = GuestCompatibilityRuntime.udevMonitorLibrary(
                app, profile.compatibility.enablesUdevMonitorShim,
            )?.absolutePath.orEmpty(),
            clipboard = AndroidTerminalClipboard(app),
        ).also { next ->
            if (profile.display == ProfileStore.DISPLAY_X11 || profile.display == ProfileStore.DISPLAY_WAYLAND) {
                next.start()
            }
        }
        runtimeSessions[profile.id] = RuntimeSession(next, profile.display, profile.graphics.renderer)
        displayedProfileId = profile.id
        session = next
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
            WaylandBridge.stop()
        }
        if (runtimeSessions.values.none { it.renderer == ProfileStore.GRAPHICS_VIRGL }) {
            VirglHostController.stop()
        }
    }

    private data class RuntimeSession(
        val session: NativePtySession,
        val display: String,
        val renderer: String,
    )

    private fun x11SocketDirectory(display: String): String? =
        X11Runtime.socketDirectory(app).absolutePath.takeIf {
            display == ProfileStore.DISPLAY_X11
        }

    private fun waylandRuntimeDirectory(display: String): String? =
        File(app.filesDir, "wayland/runtime").also { it.mkdirs() }.absolutePath.takeIf {
            display == ProfileStore.DISPLAY_WAYLAND
        }

    private fun waylandImeBridge(display: String): String? =
        if (display == ProfileStore.DISPLAY_WAYLAND) {
            WaylandImeBridgeRuntime.executable(app).absolutePath
        } else {
            null
        }
}
