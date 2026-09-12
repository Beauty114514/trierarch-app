package app.trierarch.runtime

import android.content.Context
import app.trierarch.config.ProfileStore
import app.trierarch.terminal.DefaultTerminalViewModel

/** The single runtime control surface shared by the CLI and temporary UI. */
class RuntimeController(
    context: Context,
    private val terminal: DefaultTerminalViewModel,
    private val displayHost: DisplayHost,
) {
    private val store = ProfileStore(context.applicationContext)
    private var pending: Pending? = null

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
        if (id != null) {
            completion(RuntimeCommandResult(true, "$id\t${stateOf(id)}"))
            return
        }
        val profiles = store.list().map { it.nameWithoutExtension }
        val message = if (profiles.isEmpty()) {
            "No profiles."
        } else {
            profiles.joinToString("\n") { profile -> "$profile\t${stateOf(profile)}" }
        }
        completion(RuntimeCommandResult(true, message))
    }

    private fun start(id: String, completion: (RuntimeCommandResult) -> Unit) {
        if (terminal.activeProfileId() != null) {
            completion(RuntimeCommandResult(false, "a runtime is already running: ${terminal.activeProfileId()}"))
            return
        }
        if (pending != null) {
            completion(RuntimeCommandResult(false, "runtime action in progress: ${pending?.id}"))
            return
        }
        val profile = loadProfile(id, completion) ?: return
        scheduleStart(id, profile, "starting", completion)
    }

    private fun rerun(id: String, completion: (RuntimeCommandResult) -> Unit) {
        if (terminal.activeProfileId() != id) {
            completion(RuntimeCommandResult(false, "profile is not running: $id; use --run $id"))
            return
        }
        if (pending != null) {
            completion(RuntimeCommandResult(false, "runtime action in progress: ${pending?.id}"))
            return
        }
        val profile = loadProfile(id, completion) ?: return
        pending = Pending(id, "restarting")
        completion(
            RuntimeCommandResult(true, "restarting $id") {
                displayHost.runOnMain {
                    terminal.stopRuntime()
                    displayHost.showTerminal()
                    launch(id, profile)
                }
            },
        )
    }

    private fun stop(id: String?, completion: (RuntimeCommandResult) -> Unit) {
        val active = terminal.activeProfileId()
        if (active == null) {
            completion(RuntimeCommandResult(false, "no runtime is running"))
            return
        }
        if (id != null && id != active) {
            completion(RuntimeCommandResult(false, "profile '$id' is not running; active profile is '$active'"))
            return
        }
        if (pending != null) {
            completion(RuntimeCommandResult(false, "runtime action in progress: ${pending?.id}"))
            return
        }
        pending = Pending(active, "stopping")
        completion(
            RuntimeCommandResult(true, "stopping $active") {
                displayHost.runOnMain {
                    terminal.stopRuntime()
                    displayHost.showTerminal()
                    pending = null
                }
            },
        )
    }

    private fun scheduleStart(
        id: String,
        profile: LoadedProfile,
        state: String,
        completion: (RuntimeCommandResult) -> Unit,
    ) {
        pending = Pending(id, state)
        completion(
            RuntimeCommandResult(true, "$state $id") {
                displayHost.runOnMain { launch(id, profile) }
            },
        )
    }

    private fun loadProfile(id: String, completion: (RuntimeCommandResult) -> Unit): LoadedProfile? {
        val file = store.list().firstOrNull { it.nameWithoutExtension == id }
            ?: run {
                completion(RuntimeCommandResult(false, "profile does not exist: $id"))
                return null
            }
        val content = runCatching { store.read(file) }.getOrElse {
            completion(RuntimeCommandResult(false, it.message ?: "unable to read profile $id"))
            return null
        }
        val parsed = runCatching { store.validate(content); store.runtime(content) to store.display(content) }
            .getOrElse {
                completion(RuntimeCommandResult(false, it.message ?: "invalid profile $id"))
                return null
            }
        return LoadedProfile(parsed.first, parsed.second, content)
    }

    private fun launch(id: String, profile: LoadedProfile) {
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
        action.onFailure {
            pending = null
            displayHost.showTerminal()
        }.onSuccess { start ->
            if (profile.display == ProfileStore.DISPLAY_X11) {
                displayHost.showX11(
                    onReady = {
                        runCatching(start).onSuccess {
                            pending = null
                            displayHost.attachTerminal()
                        }.onFailure {
                            pending = null
                            displayHost.showTerminal()
                        }
                    },
                    onFailure = {
                        pending = null
                        displayHost.showTerminal()
                    },
                )
            } else {
                runCatching {
                    if (profile.display == ProfileStore.DISPLAY_WAYLAND) displayHost.showWayland()
                    else displayHost.showTerminal()
                    start()
                    pending = null
                    displayHost.attachTerminal()
                }.onFailure {
                    pending = null
                    displayHost.showTerminal()
                }
            }
        }
    }

    private fun stateOf(id: String): String = pending?.takeIf { it.id == id }?.state
        ?: if (terminal.activeProfileId() == id) "running" else "stopped"

    private data class LoadedProfile(
        val runtime: String,
        val display: String,
        val content: String,
    )

    private data class Pending(val id: String, val state: String)
}
