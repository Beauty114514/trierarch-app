package app.trierarch.terminal

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import app.trierarch.core.ProfileService
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onRuntimeExited: ((String, Boolean) -> Unit)? = null

    /** The PTY currently displayed by the terminal surface. */
    var session: NativePtySession = internalSession
        private set

    /** Called on the main thread after a guest exits and its resources are released. */
    fun setRuntimeExitListener(listener: ((String, Boolean) -> Unit)?) {
        onRuntimeExited = listener
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

    fun restartProot(profile: ProfileService.ProotProfile) {
        launchRuntime(
            RuntimeLaunchSpec(
                profileId = profile.id,
                display = profile.display,
                renderer = profile.graphics.renderer,
                bufferMode = profile.graphics.resolvedBuffer,
                desktopScalePercent = profile.desktopScale,
                audioBackend = profile.audioBackend,
                enablesUdevMonitorShim = profile.compatibility.enablesUdevMonitorShim,
                installGbmShim = true,
            ),
        ) { resources ->
            NativePtySession.proot(
                ProotSessionConfig(
                    rootfs = profile.rootfs,
                    shell = profile.shell,
                    nativeLibraryDirectory = File(app.applicationInfo.nativeLibraryDir),
                    cacheDirectory = app.cacheDir,
                    environment = resources.toSessionEnvironment(),
                    launchArgv = profile.launchArgv.orEmpty(),
                    graphicsEnvironment = profile.graphics.environment(),
                    diagnosticLog = sessionDiagnosticLog(profile.id),
                ),
                clipboard = AndroidTerminalClipboard(app),
            )
        }
    }

    fun restartChroot(profile: ProfileService.ChrootProfile) {
        launchRuntime(
            RuntimeLaunchSpec(
                profileId = profile.id,
                display = profile.display,
                renderer = profile.graphics.renderer,
                bufferMode = profile.graphics.resolvedBuffer,
                desktopScalePercent = profile.desktopScale,
                audioBackend = profile.audioBackend,
                enablesUdevMonitorShim = profile.compatibility.enablesUdevMonitorShim,
                installGbmShim = false,
            ),
        ) { resources ->
            NativePtySession.chroot(
                ChrootSessionConfig(
                    rootfs = File(profile.rootfs),
                    shell = profile.shell,
                    environment = resources.toSessionEnvironment(),
                    chrootSupervisor = WaylandImeBridgeRuntime.supervisor(app),
                    launchArgv = profile.launchArgv.orEmpty(),
                    graphicsEnvironment = profile.graphics.environment(),
                    diagnosticLog = sessionDiagnosticLog(profile.id),
                ),
                clipboard = AndroidTerminalClipboard(app),
            )
        }
    }

    fun restartDroidspaces(profile: ProfileService.DroidspacesProfile) {
        launchRuntime(
            RuntimeLaunchSpec(
                profileId = profile.id,
                display = profile.display,
                renderer = profile.graphics.renderer,
                bufferMode = profile.graphics.resolvedBuffer,
                desktopScalePercent = profile.desktopScale,
                audioBackend = profile.audioBackend,
                enablesUdevMonitorShim = profile.compatibility.enablesUdevMonitorShim,
                installGbmShim = true,
            ),
        ) { resources ->
            NativePtySession.droidspaces(
                DroidspacesSessionConfig(
                    profile = profile,
                    environment = resources.toSessionEnvironment(),
                    droidspacesSupervisor = WaylandImeBridgeRuntime.droidspacesSupervisor(app),
                    diagnosticLog = sessionDiagnosticLog(profile.id),
                ),
                clipboard = AndroidTerminalClipboard(app),
            )
        }
    }

    fun isRuntimeRunning(): Boolean = runningProfileIds().isNotEmpty()

    /** The profile currently attached to Trierarch's foreground terminal surface. */
    fun activeProfileId(): String? = displayedProfileId?.takeIf { isProfileRunning(it) }

    fun isProfileRunning(id: String): Boolean = runtimeSessions[id]?.session?.isRunning() == true

    /** The tail of the last session transcript, or null when it never wrote one. */
    fun readSessionLog(id: String, lines: Int): String? =
        tailSessionLog(sessionDiagnosticLog(id), lines)

    /** The display a running session was started with, or null when it is stopped. */
    fun displayOf(id: String): String? =
        runtimeSessions[id]?.takeIf { it.session.isRunning() }?.display

    /** Running sessions keyed by profile id, valued by their display type. */
    fun runningDisplays(): Map<String, String> = runtimeSessions
        .filterValues { it.session.isRunning() }
        .mapValues { (_, runtime) -> runtime.display }

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
            session = usableInternalSession()
            return
        }
        val wasDisplayed = displayedProfileId == id
        closeRuntime(id)
        if (wasDisplayed) session = usableInternalSession()
    }

    override fun onCleared() {
        stopRuntime()
        internalSession.close()
    }

    private fun createInternalShell() = NativePtySession.command(
        CommandSessionConfig(
            launchSpec = InternalShellLaunchSpec.create(
                filesDirectory = app.filesDir,
                cacheDirectory = app.cacheDir,
                nativeLibraryDirectory = File(app.applicationInfo.nativeLibraryDir),
            ),
        ),
        clipboard = AndroidTerminalClipboard(app),
    )

    private fun usableInternalSession(): NativePtySession {
        if (!internalSession.isRunning()) internalSession = createInternalShell()
        return internalSession
    }

    private fun closeRuntime(id: String) {
        val runtime = runtimeSessions.remove(id) ?: return
        runtime.session.setExitListener(null)
        runtime.session.close()
        if (displayedProfileId == id) displayedProfileId = null
        stopUnusedHosts()
    }

    private fun stopUnusedHosts() {
        if (runtimeSessions.values.none { it.display == ProfileService.DISPLAY_WAYLAND }) {
            runtimeBootstrap.stopWayland()
        }
        if (runtimeSessions.values.none { usesVtestTransport(it.renderer) }) {
            runtimeBootstrap.stopVirgl()
        }
        if (runtimeSessions.values.none { it.pipewire }) {
            runtimeBootstrap.stopPipeWire()
        }
    }

    private fun handleRuntimeExit(id: String, exited: NativePtySession) {
        mainHandler.post {
            val runtime = runtimeSessions[id] ?: return@post
            if (runtime.session !== exited) return@post
            val wasDisplayed = displayedProfileId == id
            runtimeSessions.remove(id)
            exited.setExitListener(null)
            exited.close()
            if (displayedProfileId == id) displayedProfileId = null
            stopUnusedHosts()
            if (wasDisplayed) {
                session = usableInternalSession()
                session.appendStatus("\r\n[Trierarch session '$id' exited]\r\n")
            }
            onRuntimeExited?.invoke(id, wasDisplayed)
        }
    }

    private fun launchRuntime(
        spec: RuntimeLaunchSpec,
        createSession: (RuntimeResources) -> NativePtySession,
    ) {
        val resources = runtimeBootstrap.prepareHosts(spec)
        val next = try {
            val sessionResources = runtimeBootstrap.finishResources(spec, resources)
            createSession(sessionResources).also { session ->
                session.setExitListener { handleRuntimeExit(spec.profileId, session) }
                if (spec.display == ProfileService.DISPLAY_X11 ||
                    spec.display == ProfileService.DISPLAY_WAYLAND
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
            spec.audioBackend == ProfileService.AUDIO_PIPEWIRE,
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
        renderer == ProfileService.GRAPHICS_VIRGL || renderer == ProfileService.GRAPHICS_VENUS

    private fun sessionDiagnosticLog(profileId: String): File =
        File(app.filesDir, "logs/sessions/$profileId.log")
}
