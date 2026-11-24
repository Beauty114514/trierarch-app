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
    private val sourcesByProfile = mutableMapOf<String, LaunchSource>()
    private val sourcesStartedByTrierarch = mutableSetOf<String>()

    interface DisplayHost {
        fun attachTerminal()
        fun showInternalTerminal()
        fun showRuntimeTerminal()
        fun showWayland()
        fun showX11(onReady: () -> Unit, onFailure: (String) -> Unit)
        fun showGui()
        fun showSession(id: String, display: String)
        fun runOnMain(action: () -> Unit)
    }

    fun dispatch(command: Command, completion: (CommandOutcome) -> Unit) {
        when (command) {
            is Command.Run -> start(command.id, completion)
            is Command.Open -> openSession(command.id, completion)
            is Command.Stop -> stop(command.id, completion)
            // Switching screens must never start, stop, or rebuild a session.
            is Command.OpenGui -> openGui(completion)
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

    private fun openGui(completion: (CommandOutcome) -> Unit) {
        displayHost.showGui()
        completion(CommandOutcome.Success("gui opened"))
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
        val source = sourcesByProfile[id] ?: loadProfileSilently(id)?.source
        val sourceState = source?.state()?.let { " source=$it" }.orEmpty()
        return "$id\t${stateOf(id)}$sourceState"
    }

    private fun start(id: String, completion: (CommandOutcome) -> Unit) {
        if (terminal.isProfileRunning(id)) {
            completion(CommandOutcome.Failure(CommandError.AlreadyRunning(id)))
            return
        }
        if (pending != null) {
            completion(CommandOutcome.Failure(CommandError.Busy(pending?.id)))
            return
        }
        val profile = loadProfile(id, completion) ?: return
        // X11 and Wayland are single hosts; a second session would show the
        // first one on screen, so refuse instead of lying about what is shown.
        val conflict = findDisplayConflict(terminal.runningDisplays(), id, profile.display)
        if (conflict != null) {
            completion(CommandOutcome.Failure(CommandError.DisplayBusy(profile.display, conflict)))
            return
        }
        val sourceWasRunning = sourceWasRunning(profile.source, completion) ?: return
        scheduleStart(id, profile, sourceWasRunning, "starting", completion)
    }

    private fun stop(id: String?, completion: (CommandOutcome) -> Unit) {
        val running = terminal.runningProfileIds()
        val target = id ?: terminal.activeProfileId() ?: running.singleOrNull()
        if (target == null) {
            val error = if (running.isEmpty()) {
                CommandError.NoSession
            } else {
                CommandError.Ambiguous(running.toList())
            }
            completion(CommandOutcome.Failure(error))
            return
        }
        if (!terminal.isProfileRunning(target)) {
            completion(CommandOutcome.Failure(CommandError.NotRunning(target)))
            return
        }
        if (pending != null) {
            completion(CommandOutcome.Failure(CommandError.Busy(pending?.id)))
            return
        }
        pending = Pending(target, STATE_STOPPING)
        completion(CommandOutcome.Success("stopping $target") {
            displayHost.runOnMain {
                val source = sourcesByProfile.remove(target) ?: loadProfileSilently(target)?.source
                // Only fall back to the shell when the stopped session owned the
                // display; stopping a background session must not yank the
                // currently shown desktop away.
                val wasDisplayed = terminal.activeProfileId() == target
                terminal.stopRuntime(target)
                source?.let(::stopSourceIfUnused)
                if (wasDisplayed) displayHost.showInternalTerminal()
                pending = null
            }
        })
    }

    private fun scheduleStart(
        id: String,
        profile: LoadedProfile,
        sourceWasRunning: Boolean,
        state: String,
        completion: (CommandOutcome) -> Unit,
    ) {
        pending = Pending(id, state)
        completion(CommandOutcome.Success("$state $id") {
            displayHost.runOnMain { launch(id, profile, sourceWasRunning) }
        })
    }

    private fun launch(id: String, profile: LoadedProfile, sourceWasRunning: Boolean) {
        val action = when (profile.runtime) {
            ProfileService.RUNTIME_INTERNAL_SHELL -> Result.success { terminal.restartInternalShell() }
            ProfileService.RUNTIME_PROOT -> runCatching { profiles.prootProfile(profile.content) }
                .map { typed -> { terminal.restartProot(typed) } }
            ProfileService.RUNTIME_CHROOT -> runCatching { profiles.chrootProfile(profile.content) }
                .map { typed -> { terminal.restartChroot(typed) } }
            ProfileService.RUNTIME_DROIDSPACES -> runCatching { profiles.droidspacesProfile(profile.content) }
                .map { typed -> { terminal.restartDroidspaces(typed) } }
            else -> Result.failure(IllegalArgumentException("runtime '${profile.runtime}' cannot be started"))
        }
        action.onFailure { error ->
            launchFailed(id, error)
        }.onSuccess { start ->
            val started = {
                start()
                sourcesByProfile[id] = profile.source
                if (!sourceWasRunning && profile.source is LaunchSource.Droidspaces) {
                    sourcesStartedByTrierarch += profile.source.key
                }
                pending = null
                displayHost.attachTerminal()
            }
            if (profile.display == ProfileService.DISPLAY_X11) {
                displayHost.showX11(
                    onReady = { runCatching(started).onFailure { launchFailed(id, it) } },
                    onFailure = { launchFailed(id, IllegalStateException(it)) },
                )
            } else if (profile.display == ProfileService.DISPLAY_WAYLAND) {
                runCatching {
                    displayHost.showWayland()
                    started()
                }.onFailure { launchFailed(id, it) }
            } else {
                runCatching {
                    started()
                    if (profile.runtime == ProfileService.RUNTIME_INTERNAL_SHELL) {
                        displayHost.showInternalTerminal()
                    } else {
                        displayHost.showRuntimeTerminal()
                    }
                }.onFailure { launchFailed(id, it) }
            }
        }
    }

    private fun launchFailed(id: String, error: Throwable) {
        sourcesByProfile.remove(id)
        pending = null
        terminal.reportRuntimeLaunchFailure(id, error)
        displayHost.showInternalTerminal()
    }

    private fun stopSourceIfUnused(source: LaunchSource) {
        if (terminal.runningProfileIds().any { sourcesByProfile[it]?.key == source.key }) return
        if (source is LaunchSource.Droidspaces && sourcesStartedByTrierarch.remove(source.key)) {
            runCatching { NativePtyBridge.stopDroidspaces(source.container) }
        }
    }

    private fun sourceWasRunning(
        source: LaunchSource,
        completion: (CommandOutcome) -> Unit,
    ): Boolean? = if (source !is LaunchSource.Droidspaces) {
        true
    } else {
        runCatching {
            NativePtyBridge.isDroidspacesRunning(source.container)
        }.getOrElse {
            completion(
                CommandOutcome.Failure(
                    CommandError.Failed(it.message ?: "unable to inspect source ${source.key}"),
                ),
            )
            return null
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

    private data class LoadedProfile(
        val runtime: String,
        val display: String,
        val content: String,
        val source: LaunchSource,
    )

    private data class Pending(val id: String, val state: String)

    private sealed interface LaunchSource {
        val key: String
        fun state(): String

        data class Droidspaces(val container: String) : LaunchSource {
            override val key = "droidspaces:$container"
            override fun state(): String = "$key:${if (runCatching { NativePtyBridge.isDroidspacesRunning(container) }.getOrDefault(false)) "running" else "stopped"}"
        }
        data class Proot(val rootfs: String) : LaunchSource {
            override val key = "proot:$rootfs"
            override fun state(): String = "$key:session-scoped"
        }
        data class Chroot(val rootfs: String) : LaunchSource {
            override val key = "chroot:$rootfs"
            override fun state(): String = "$key:session-scoped"
        }
        data object Internal : LaunchSource {
            override val key = "internal"
            override fun state(): String = "internal:session-scoped"
        }
    }

    private companion object {
        const val STATE_STOPPING = "stopping"
    }
}
