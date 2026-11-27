package app.trierarch.core

import app.trierarch.nativebridge.NativePtyBridge
import app.trierarch.terminal.DefaultTerminalViewModel

/** Owns profile sessions separately from the containers/rootfs they enter. */
class SessionService(
    private val profiles: ProfileService,
    private val terminal: DefaultTerminalViewModel,
    private val displayHost: DisplayHost,
) {
    private var pending: Pending? = null
    private val sessions = LinkedHashMap<String, ManagedSession>()
    private var onSessionsChanged: (() -> Unit)? = null

    init {
        terminal.setRuntimeExitListener(::handleRuntimeExit)
    }

    interface DisplayHost {
        fun attachTerminal()
        fun showInternalTerminal()
        fun showRuntimeTerminal()
        fun showWayland()
        fun showX11(onReady: () -> Unit, onFailure: (String) -> Unit)
        fun showSession(id: String, display: String)
        fun releaseX11()
        fun runOnMain(action: () -> Unit)
    }

    fun setSessionsChangedListener(listener: (() -> Unit)?) {
        onSessionsChanged = listener
    }

    fun dispatch(command: Command.Session, completion: (CommandOutcome) -> Unit) {
        when (command) {
            is Command.Session.Run -> start(command.id, completion)
            is Command.Session.Open -> openSession(command.id, completion)
            is Command.Session.Stop -> stop(command.id, completion)
            // Switching screens must never start, stop, or rebuild a session.
        }
    }

    /**
     * Shows an already-running session. It never starts, stops or restarts a
     * runtime and does not re-read the profile: only the display changes.
     */
    private fun openSession(id: String, completion: (CommandOutcome) -> Unit) {
        val display = terminal.displayOf(id) ?: run {
            completion(CommandOutcome.Failure(CommandError.NotRunning(id, useRunHint = true)))
            return
        }
        completion(
            CommandOutcome.Success("opening $id") {
                displayHost.runOnMain { displayHost.showSession(id, display) }
            },
        )
    }



    /** Structured runtime state for the GUI; the CLI keeps its text status. */
    fun snapshot(): RuntimeSnapshot {
        val pendingNow = pending
        return buildRuntimeSnapshot(
            running = terminal.runningProfileIds(),
            active = terminal.activeProfileId(),
            pendingId = pendingNow?.id,
            pendingStopping = pendingNow?.state == STATE_STOPPING,
        )
    }

    /** The tail of the last session transcript for [id], or null when none exists. */
    fun logs(id: String, lines: Int): String? = terminal.readSessionLog(id, lines)

    /** The CLI's text view of the session table; also a query for the adapter. */
    fun status(id: String?): CommandOutcome {
        if (id != null && loadProfileSilently(id) == null) {
            return CommandOutcome.Failure(CommandError.InvalidDocument(id))
        }
        val ids = if (id == null) profiles.list().map { it.nameWithoutExtension } else listOf(id)
        if (ids.isEmpty()) return CommandOutcome.Success("No profiles.")
        return CommandOutcome.Success(ids.joinToString("\n") { statusLine(it) })
    }

    private fun statusLine(id: String): String {
        val source = sessions[id]?.source ?: loadProfileSilently(id)?.source
        val sourceState = source?.state()?.let { " source=$it" }.orEmpty()
        return "$id\t${stateOf(id)}$sourceState"
    }

    private fun start(id: String, completion: (CommandOutcome) -> Unit) {
        // A pending action owns the lifecycle; report that before any conflict.
        if (pending != null) {
            completion(CommandOutcome.Failure(CommandError.Busy(pending?.id)))
            return
        }
        if (terminal.isProfileRunning(id)) {
            completion(CommandOutcome.Failure(CommandError.AlreadyRunning(id)))
            return
        }
        val profile = loadProfile(id, completion) ?: return
        // The internal-shell profile is the app's own management shell, not a
        // runtime: showing it must not stop whatever is already running.
        if (profile.runtime == ProfileService.RUNTIME_INTERNAL_SHELL) {
            completion(
                CommandOutcome.Success("starting $id") {
                    displayHost.runOnMain {
                        terminal.showInternalShell()
                        displayHost.showInternalTerminal()
                    }
                },
            )
            return
        }

        // One container/rootfs can back at most one session, in any display mode.
        val slots = sessions.values.map { SessionSlot(it.profileId, it.display, it.source.key) }
        findSourceConflict(slots, id, profile.source.key)?.let { other ->
            completion(
                CommandOutcome.Failure(
                    CommandError.SourceBusy(profile.source.label(), other.profileId),
                ),
            )
            return
        }
        // X11 and Wayland are single hosts; a second session would show the
        // first one on screen, so refuse instead of lying about what is shown.
        findDisplayConflict(slots, id, profile.display)?.let { other ->
            completion(
                CommandOutcome.Failure(CommandError.DisplayBusy(profile.display, other.profileId)),
            )
            return
        }
        scheduleStart(id, profile, "starting", completion)
    }

    private fun stop(id: String?, completion: (CommandOutcome) -> Unit) {
        // A pending launch/stop owns the lifecycle; do not report "stopped"
        // for a session that is still starting behind it.
        if (pending != null) {
            completion(CommandOutcome.Failure(CommandError.Busy(pending?.id)))
            return
        }
        val running = terminal.runningProfileIds()
        if (id == null && running.size > 1) {
            completion(CommandOutcome.Failure(CommandError.Ambiguous(running.toList())))
            return
        }
        val target = id ?: running.singleOrNull()
        if (target == null || !terminal.isProfileRunning(target)) {
            // Stop is idempotent: nothing to do is a successful outcome.
            completion(CommandOutcome.Success("already stopped"))
            return
        }
        pending = Pending(target, STATE_STOPPING)
        completion(CommandOutcome.Success("stopping $target") {
            displayHost.runOnMain { teardown(target) }
        })
    }

    /**
     * Ends one session and the whole environment behind it: the PTY, the
     * display host if it was showing it, and the container (whether Trierarch
     * started the container or merely attached to it).
     */
    private fun teardown(id: String) {
        val wasDisplayed = terminal.activeProfileId() == id
        val session = sessions.remove(id)
        terminal.stopRuntime(id)
        session?.let { stopSource(it.source) }
        if (wasDisplayed) displayHost.showInternalTerminal()
        if (session?.display == ProfileService.DISPLAY_X11) displayHost.releaseX11()
        pending = null
        notifySessionsChanged()
    }

    /** A guest process exited on its own; reuse the stop path's cleanup. */
    private fun handleRuntimeExit(id: String, wasDisplayed: Boolean) {
        val session = sessions.remove(id)
        session?.let { stopSource(it.source) }
        if (pending?.id == id) pending = null
        if (wasDisplayed) displayHost.showInternalTerminal()
        if (session?.display == ProfileService.DISPLAY_X11) displayHost.releaseX11()
        notifySessionsChanged()
    }

    private fun scheduleStart(
        id: String,
        profile: LoadedProfile,
        state: String,
        completion: (CommandOutcome) -> Unit,
    ) {
        pending = Pending(id, state)
        completion(CommandOutcome.Success("$state $id") {
            displayHost.runOnMain { launch(id, profile) }
        })
    }

    private fun launch(id: String, profile: LoadedProfile) {
        val action = when (profile.runtime) {
            ProfileService.RUNTIME_PROOT -> runCatching { profiles.prootProfile(profile.content) }
                .map { typed -> { terminal.restartProot(typed) } }
            ProfileService.RUNTIME_CHROOT -> runCatching { profiles.chrootProfile(profile.content) }
                .map { typed -> { terminal.restartChroot(typed) } }
            ProfileService.RUNTIME_DROIDSPACES -> runCatching { profiles.droidspacesProfile(profile.content) }
                .map { typed -> { terminal.restartDroidspaces(typed) } }
            else -> Result.failure(IllegalArgumentException("runtime '${profile.runtime}' cannot be started"))
        }
        action.onFailure { error ->
            launchFailed(id, profile.display, error)
        }.onSuccess { start ->
            val started = {
                start()
                sessions[id] = ManagedSession(id, profile.display, profile.source)
                pending = null
                displayHost.attachTerminal()
                notifySessionsChanged()
            }
            if (profile.display == ProfileService.DISPLAY_X11) {
                runCatching {
                    displayHost.showX11(
                        onReady = {
                            runCatching(started).onFailure { launchFailed(id, profile.display, it) }
                        },
                        onFailure = { launchFailed(id, profile.display, IllegalStateException(it)) },
                    )
                }.onFailure { launchFailed(id, profile.display, it) }
            } else if (profile.display == ProfileService.DISPLAY_WAYLAND) {
                runCatching {
                    displayHost.showWayland()
                    started()
                }.onFailure { launchFailed(id, profile.display, it) }
            } else {
                runCatching {
                    started()
                    displayHost.showRuntimeTerminal()
                }.onFailure { launchFailed(id, profile.display, it) }
            }
        }
    }

    private fun launchFailed(id: String, display: String, error: Throwable) {
        sessions.remove(id)
        pending = null
        terminal.reportRuntimeLaunchFailure(id, error)
        displayHost.showInternalTerminal()
        if (display == ProfileService.DISPLAY_X11) displayHost.releaseX11()
        notifySessionsChanged()
    }

    /** The session owned the source; stopping it owns tearing the container down. */
    private fun stopSource(source: LaunchSource) {
        if (source is LaunchSource.Droidspaces) {
            runCatching { NativePtyBridge.stopDroidspaces(source.container) }
        }
    }

    private fun loadProfile(id: String, completion: (CommandOutcome) -> Unit): LoadedProfile? =
        loadProfileSilently(id) ?: run {
            completion(CommandOutcome.Failure(CommandError.InvalidDocument(id)))
            null
        }

    private fun loadProfileSilently(id: String): LoadedProfile? = runCatching {
        val file = profiles.list().firstOrNull { it.nameWithoutExtension == id } ?: return null
        val content = profiles.read(file)
        profiles.validate(content)
        val runtime = profiles.runtime(content)
        val source = when (runtime) {
            ProfileService.RUNTIME_DROIDSPACES ->
                LaunchSource.Droidspaces(profiles.droidspacesProfile(content).container)
            ProfileService.RUNTIME_PROOT ->
                LaunchSource.Proot(profiles.prootProfile(content).rootfs.absolutePath)
            ProfileService.RUNTIME_CHROOT ->
                LaunchSource.Chroot(profiles.chrootProfile(content).rootfs)
            ProfileService.RUNTIME_INTERNAL_SHELL -> LaunchSource.Internal
            else -> throw IllegalArgumentException("unknown runtime '$runtime'")
        }
        LoadedProfile(runtime, profiles.display(content), content, source)
    }.getOrNull()

    private fun stateOf(id: String): String = pending?.takeIf { it.id == id }?.state
        ?: if (terminal.isProfileRunning(id)) "running" else "stopped"

    private fun notifySessionsChanged() {
        onSessionsChanged?.invoke()
    }

    private data class LoadedProfile(
        val runtime: String,
        val display: String,
        val content: String,
        val source: LaunchSource,
    )

    private data class ManagedSession(
        val profileId: String,
        val display: String,
        val source: LaunchSource,
    )

    private data class Pending(val id: String, val state: String)

    private sealed interface LaunchSource {
        val key: String
        fun state(): String
        fun label(): String

        data class Droidspaces(val container: String) : LaunchSource {
            override val key = "droidspaces:$container"
            override fun state(): String = "$key:${if (runCatching { NativePtyBridge.isDroidspacesRunning(container) }.getOrDefault(false)) "running" else "stopped"}"
            override fun label(): String = "container '$container'"
        }
        data class Proot(val rootfs: String) : LaunchSource {
            override val key = "proot:$rootfs"
            override fun state(): String = "$key:session-scoped"
            override fun label(): String = "rootfs '$rootfs'"
        }
        data class Chroot(val rootfs: String) : LaunchSource {
            override val key = "chroot:$rootfs"
            override fun state(): String = "$key:session-scoped"
            override fun label(): String = "rootfs '$rootfs'"
        }
        data object Internal : LaunchSource {
            override val key = "internal"
            override fun state(): String = "internal:session-scoped"
            override fun label(): String = "internal shell"
        }
    }

    private companion object {
        const val STATE_STOPPING = "stopping"
    }
}
