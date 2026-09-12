package app.trierarch.runtime

import android.content.Context
import app.trierarch.config.ProfileStore
import app.trierarch.nativebridge.NativePtyBridge
import app.trierarch.terminal.DefaultTerminalViewModel

/** Controls profile sessions separately from the containers/rootfs they enter. */
class RuntimeController(
    context: Context,
    private val terminal: DefaultTerminalViewModel,
    private val displayHost: DisplayHost,
) {
    private val store = ProfileStore(context.applicationContext)
    private var pending: Pending? = null
    private val sourcesByProfile = mutableMapOf<String, LaunchSource>()
    private val sourcesStartedByTrierarch = mutableSetOf<String>()

    interface DisplayHost {
        fun attachTerminal()
        fun showTerminal()
        fun showWayland()
        fun showX11(onReady: () -> Unit, onFailure: (String) -> Unit)
        fun runOnMain(action: () -> Unit)
    }

    fun dispatch(command: RuntimeCommand, completion: (RuntimeCommandResult) -> Unit) {
        when (command) {
            is RuntimeCommand.Status -> status(command.id, completion)
            is RuntimeCommand.Run -> start(command.id, completion)
            is RuntimeCommand.Stop -> stop(command.id, completion)
            is RuntimeCommand.Rerun -> rerun(command.id, completion)
        }
    }

    private fun status(id: String?, completion: (RuntimeCommandResult) -> Unit) {
        if (id != null && loadProfileSilently(id) == null) {
            completion(RuntimeCommandResult(false, "profile does not exist or is invalid: $id"))
            return
        }
        val profiles = if (id == null) store.list().map { it.nameWithoutExtension } else listOf(id)
        if (profiles.isEmpty()) {
            completion(RuntimeCommandResult(true, "No profiles."))
            return
        }
        completion(RuntimeCommandResult(true, profiles.joinToString("\n") { profile -> statusLine(profile) }))
    }

    private fun statusLine(id: String): String {
        val source = sourcesByProfile[id] ?: loadProfileSilently(id)?.source
        val sourceState = source?.state()?.let { " source=$it" }.orEmpty()
        return "$id\t${stateOf(id)}$sourceState"
    }

    private fun start(id: String, completion: (RuntimeCommandResult) -> Unit) {
        if (terminal.isProfileRunning(id)) {
            completion(RuntimeCommandResult(false, "profile is already running: $id"))
            return
        }
        if (pending != null) {
            completion(RuntimeCommandResult(false, "runtime action in progress: ${pending?.id}"))
            return
        }
        val profile = loadProfile(id, completion) ?: return
        val sourceWasRunning = sourceWasRunning(profile.source, completion) ?: return
        scheduleStart(id, profile, sourceWasRunning, "starting", completion)
    }

    private fun rerun(id: String, completion: (RuntimeCommandResult) -> Unit) {
        if (!terminal.isProfileRunning(id)) {
            completion(RuntimeCommandResult(false, "profile is not running: $id; use --run $id"))
            return
        }
        if (pending != null) {
            completion(RuntimeCommandResult(false, "runtime action in progress: ${pending?.id}"))
            return
        }
        val profile = loadProfile(id, completion) ?: return
        pending = Pending(id, "restarting")
        completion(RuntimeCommandResult(true, "restarting $id") {
            displayHost.runOnMain {
                terminal.stopRuntime(id)
                launch(id, profile, sourceWasRunning = true)
            }
        })
    }

    private fun stop(id: String?, completion: (RuntimeCommandResult) -> Unit) {
        val running = terminal.runningProfileIds()
        val target = id ?: terminal.activeProfileId() ?: running.singleOrNull()
        if (target == null) {
            completion(RuntimeCommandResult(false, if (running.isEmpty()) "no runtime is running" else "multiple profiles are running; specify an ID"))
            return
        }
        if (!terminal.isProfileRunning(target)) {
            completion(RuntimeCommandResult(false, "profile is not running: $target"))
            return
        }
        if (pending != null) {
            completion(RuntimeCommandResult(false, "runtime action in progress: ${pending?.id}"))
            return
        }
        pending = Pending(target, "stopping")
        completion(RuntimeCommandResult(true, "stopping $target") {
            displayHost.runOnMain {
                val source = sourcesByProfile.remove(target) ?: loadProfileSilently(target)?.source
                terminal.stopRuntime(target)
                source?.let(::stopSourceIfUnused)
                displayHost.showTerminal()
                pending = null
            }
        })
    }

    private fun scheduleStart(
        id: String,
        profile: LoadedProfile,
        sourceWasRunning: Boolean,
        state: String,
        completion: (RuntimeCommandResult) -> Unit,
    ) {
        pending = Pending(id, state)
        completion(RuntimeCommandResult(true, "$state $id") {
            displayHost.runOnMain { launch(id, profile, sourceWasRunning) }
        })
    }

    private fun launch(id: String, profile: LoadedProfile, sourceWasRunning: Boolean) {
        val action = when (profile.runtime) {
            ProfileStore.RUNTIME_INTERNAL_SHELL -> Result.success { terminal.restartInternalShell() }
            ProfileStore.RUNTIME_PROOT -> runCatching { store.prootProfile(profile.content) }
                .map { typed -> { terminal.restartProot(typed) } }
            ProfileStore.RUNTIME_CHROOT -> runCatching { store.chrootProfile(profile.content) }
                .map { typed -> { terminal.restartChroot(typed) } }
            ProfileStore.RUNTIME_DROIDSPACES -> runCatching { store.droidspacesProfile(profile.content) }
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
            if (profile.display == ProfileStore.DISPLAY_X11) {
                displayHost.showX11(
                    onReady = { runCatching(started).onFailure { launchFailed(id, it) } },
                    onFailure = { launchFailed(id, IllegalStateException(it)) },
                )
            } else {
                runCatching {
                    if (profile.display == ProfileStore.DISPLAY_WAYLAND) displayHost.showWayland()
                    else displayHost.showTerminal()
                    started()
                }.onFailure { launchFailed(id, it) }
            }
        }
    }

    private fun launchFailed(id: String, error: Throwable) {
        sourcesByProfile.remove(id)
        pending = null
        terminal.reportRuntimeLaunchFailure(id, error)
        displayHost.showTerminal()
    }

    private fun stopSourceIfUnused(source: LaunchSource) {
        if (terminal.runningProfileIds().any { sourcesByProfile[it]?.key == source.key }) return
        if (source is LaunchSource.Droidspaces && sourcesStartedByTrierarch.remove(source.key)) {
            runCatching { NativePtyBridge.stopDroidspaces(source.container) }
        }
    }

    private fun sourceWasRunning(source: LaunchSource, completion: (RuntimeCommandResult) -> Unit): Boolean? =
        if (source !is LaunchSource.Droidspaces) true else runCatching {
            NativePtyBridge.isDroidspacesRunning(source.container)
        }.getOrElse {
            completion(RuntimeCommandResult(false, it.message ?: "unable to inspect source ${source.key}"))
            return null
        }

    private fun loadProfile(id: String, completion: (RuntimeCommandResult) -> Unit): LoadedProfile? =
        loadProfileSilently(id) ?: run {
            completion(RuntimeCommandResult(false, "profile does not exist or is invalid: $id"))
            null
        }

    private fun loadProfileSilently(id: String): LoadedProfile? = runCatching {
        val file = store.list().firstOrNull { it.nameWithoutExtension == id } ?: return null
        val content = store.read(file)
        store.validate(content)
        val runtime = store.runtime(content)
        val source = when (runtime) {
            ProfileStore.RUNTIME_DROIDSPACES -> LaunchSource.Droidspaces(store.droidspacesProfile(content).container)
            ProfileStore.RUNTIME_PROOT -> LaunchSource.Proot(store.prootProfile(content).rootfs.absolutePath)
            ProfileStore.RUNTIME_CHROOT -> LaunchSource.Chroot(store.chrootProfile(content).rootfs)
            ProfileStore.RUNTIME_INTERNAL_SHELL -> LaunchSource.Internal
            else -> throw IllegalArgumentException("unknown runtime '$runtime'")
        }
        LoadedProfile(runtime, store.display(content), content, source)
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
}
