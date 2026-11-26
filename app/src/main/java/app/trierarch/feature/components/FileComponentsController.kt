package app.trierarch.feature.components

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FileComponentsState(
    val entries: List<FileComponentEntry> = emptyList(),
    val archives: List<File> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class FileComponentsController(
    private val store: FileComponentStore,
    private val rootfs: File,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "trierarch-file-components").apply { isDaemon = true }
    },
) {
    private val mutableState = MutableStateFlow(FileComponentsState())
    val state: StateFlow<FileComponentsState> = mutableState

    fun load() {
        executor.execute {
            runCatching {
                store.entries(rootfs) to store.availableArchives()
            }.fold(
                onSuccess = { (entries, archives) ->
                    mutableState.value = mutableState.value.copy(
                        entries = entries,
                        archives = archives,
                        error = null,
                    )
                },
                onFailure = { mutableState.value = mutableState.value.copy(error = it.message) },
            )
        }
    }

    fun install(archive: File) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null, message = null)
        executor.execute {
            runCatching { store.install(rootfs, archive) }.fold(
                onSuccess = { installed ->
                    mutableState.value = mutableState.value.copy(
                        busy = false,
                        entries = store.entries(rootfs),
                        archives = store.availableArchives(),
                        message = "Installed ${installed.joinToString(", ")}",
                    )
                },
                onFailure = {
                    mutableState.value = mutableState.value.copy(
                        busy = false,
                        error = it.message ?: "install failed",
                    )
                },
            )
        }
    }

    fun delete(entry: FileComponentEntry) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null, message = null)
        executor.execute {
            runCatching { store.delete(rootfs, entry.name) }.fold(
                onSuccess = {
                    mutableState.value = mutableState.value.copy(
                        busy = false,
                        entries = store.entries(rootfs),
                        message = "Deleted ${entry.name}",
                    )
                },
                onFailure = {
                    mutableState.value = mutableState.value.copy(
                        busy = false,
                        error = it.message ?: "delete failed",
                    )
                },
            )
        }
    }

    fun close() {
        executor.shutdownNow()
    }
}
