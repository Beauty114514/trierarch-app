package app.trierarch.feature.rootfs

import android.content.Context
import android.os.Build
import app.trierarch.core.Command
import app.trierarch.core.CommandOutcome
import app.trierarch.core.CoreApi
import app.trierarch.update.FileDownloader
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class InstalledRootfs(
    val name: String,
    val path: String,
    val modifiedAt: Long,
)

data class RootfsState(
    val loading: Boolean = false,
    val images: List<LxcImage> = emptyList(),
    val error: String? = null,
    val downloads: Map<String, DownloadState> = emptyMap(),
    val downloaded: List<DownloadedRootfs> = emptyList(),
    val installs: Map<String, InstallState> = emptyMap(),
    val installed: List<InstalledRootfs> = emptyList(),
    val loadedAt: Long = 0,
) {
    sealed interface DownloadState {
        data class Downloading(val downloaded: Long, val total: Long) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    sealed interface InstallState {
        data class Installing(val name: String) : InstallState
        data class Installed(val name: String, val path: String) : InstallState
        data class Failed(val name: String, val message: String) : InstallState
    }
}

sealed interface RootfsEvent {
    data class Message(val text: String) : RootfsEvent
}

/**
 * Download-only rootfs repository. It browses the upstream Linux Containers
 * index and writes archives into files/downloads/rootfs; it never extracts.
 */
class RootfsController(
    private val store: RootfsDownloadStore,
    private val rootfsDirectory: File,
    private val core: CoreApi,
    private val loadImages: (force: Boolean) -> List<LxcImage>,
    private val downloadFile: (
        image: LxcImage,
        destination: File,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) -> File,
    private val executor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "trierarch-rootfs-downloads").apply { isDaemon = true }
    },
) {
    constructor(context: Context, core: CoreApi) : this(
        store = RootfsDownloadStore(File(context.filesDir, "downloads/rootfs")),
        rootfsDirectory = File(context.filesDir, "rootfs"),
        core = core,
        loadImages = { force ->
            LxcCatalog(
                cacheFile = File(context.cacheDir, "lxc-images.json"),
                arch = deviceArch(),
            ).load(force)
        },
        downloadFile = { image, destination, onProgress, isCancelled ->
            FileDownloader.download(
                url = image.downloadUrl,
                destination = destination,
                expectedSize = image.size,
                expectedSha256 = image.sha256,
                onProgress = onProgress,
                shouldCancel = isCancelled,
            )
        },
    )

    private val mutableState = MutableStateFlow(RootfsState())
    private val mutableEvents = MutableSharedFlow<RootfsEvent>(extraBufferCapacity = 8)
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val executorService = executor as? ExecutorService

    val state: StateFlow<RootfsState> = mutableState
    val events: SharedFlow<RootfsEvent> = mutableEvents

    init {
        refreshDownloaded()
        refreshInstalled()
    }

    fun load(force: Boolean = false) {
        if (mutableState.value.loading) return
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        executor.execute {
            runCatching { loadImages(force) }.fold(
                onSuccess = { images ->
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        images = images,
                        downloaded = store.list(),
                        error = null,
                        loadedAt = System.currentTimeMillis(),
                    )
                    refreshInstalled()
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        error = error.message ?: "failed to load Linux Containers images",
                        downloaded = store.list(),
                    )
                },
            )
        }
    }

    fun download(image: LxcImage) {
        val current = mutableState.value
        if (current.downloaded.any { it.product == image.product }) return
        if (current.downloads[image.product] is RootfsState.DownloadState.Downloading) return

        val destination = store.fileFor(image)
        if (destination.isFile) {
            runCatching { store.record(image, destination) }
            mutableState.value = mutableState.value.copy(downloaded = store.list())
            mutableEvents.tryEmit(RootfsEvent.Message("${destination.name} is already downloaded"))
            return
        }

        cancelled.remove(image.product)
        val latest = mutableState.value
        mutableState.value = latest.copy(
            downloads = latest.downloads + (
                image.product to RootfsState.DownloadState.Downloading(0, image.size)
                ),
        )
        executor.execute {
            val result = runCatching {
                downloadFile(
                    image,
                    destination,
                    { downloaded, total ->
                        val state = mutableState.value
                        if (state.downloads[image.product] is RootfsState.DownloadState.Downloading) {
                            mutableState.value = state.copy(
                                downloads = state.downloads + (
                                    image.product to RootfsState.DownloadState.Downloading(
                                        downloaded,
                                        total,
                                    )
                                    ),
                            )
                        }
                    },
                    { cancelled.contains(image.product) },
                )
            }
            result.onSuccess { file ->
                runCatching { store.record(image, file) }
                val state = mutableState.value
                mutableState.value = state.copy(
                    downloads = state.downloads - image.product,
                    downloaded = store.list(),
                )
                mutableEvents.tryEmit(RootfsEvent.Message("Downloaded ${file.name}"))
            }.onFailure { error ->
                val wasCancelled = cancelled.remove(image.product)
                var state = mutableState.value
                state = state.copy(downloads = state.downloads - image.product)
                if (!wasCancelled) {
                    state = state.copy(
                        downloads = state.downloads + (
                            image.product to RootfsState.DownloadState.Failed(
                                error.message ?: "download failed",
                            )
                            ),
                    )
                }
                mutableState.value = state
                mutableEvents.tryEmit(
                    RootfsEvent.Message(
                        if (wasCancelled) {
                            "Download cancelled"
                        } else {
                            "Download failed: ${error.message}"
                        },
                    ),
                )
            }
        }
    }

    fun cancel(image: LxcImage) {
        if (mutableState.value.downloads[image.product] is RootfsState.DownloadState.Downloading) {
            cancelled += image.product
        }
    }

    fun remove(downloaded: DownloadedRootfs) {
        executor.execute {
            store.remove(downloaded.file)
            mutableState.value = mutableState.value.copy(downloaded = store.list())
            mutableEvents.tryEmit(RootfsEvent.Message("Deleted ${downloaded.file}"))
        }
    }

    fun install(downloaded: DownloadedRootfs, name: String) {
        val current = mutableState.value
        if (current.installs[downloaded.file] is RootfsState.InstallState.Installing) return
        val clean = name.trim()
        val validation = validateRootfsName(clean)
        if (validation != null) {
            failInstall(downloaded, clean, validation)
            return
        }
        val archive = store.file(downloaded.file)
        if (!archive.isFile) {
            failInstall(downloaded, clean, "downloaded archive no longer exists")
            return
        }
        val destination = File(rootfsDirectory, clean)
        if (destination.exists()) {
            failInstall(downloaded, clean, "rootfs name already exists: $clean")
            return
        }
        mutableState.value = mutableState.value.copy(
            installs = mutableState.value.installs + (
                downloaded.file to RootfsState.InstallState.Installing(clean)
                ),
        )
        core.submit(Command.Document.Import(archive.absolutePath, clean)) { outcome ->
            when (outcome) {
                is CommandOutcome.Failure -> failInstall(downloaded, clean, outcome.error.message)
                is CommandOutcome.Success -> {
                    mutableState.value = mutableState.value.copy(
                        installs = mutableState.value.installs + (
                            downloaded.file to RootfsState.InstallState.Installed(
                                clean,
                                destination.absolutePath,
                            )
                            ),
                    )
                    mutableEvents.tryEmit(
                        RootfsEvent.Message("Extracted $clean to files/rootfs/$clean"),
                    )
                    refreshInstalled()
                }
            }
        }
    }

    fun refreshDownloaded() {
        mutableState.value = mutableState.value.copy(downloaded = store.list())
    }

    fun refreshInstalled() {
        val installed = if (!rootfsDirectory.isDirectory) {
            emptyList()
        } else {
            rootfsDirectory.listFiles().orEmpty()
                .asSequence()
                .filter { file ->
                    file.isDirectory && !file.name.startsWith(".") &&
                        !file.name.contains(".import-")
                }
                .sortedBy { it.name.lowercase() }
                .map { file ->
                    InstalledRootfs(
                        name = file.name,
                        path = file.absolutePath,
                        modifiedAt = file.lastModified(),
                    )
                }
                .toList()
        }
        mutableState.value = mutableState.value.copy(installed = installed)
    }

    private fun failInstall(downloaded: DownloadedRootfs, name: String, message: String) {
        mutableState.value = mutableState.value.copy(
            installs = mutableState.value.installs + (
                downloaded.file to RootfsState.InstallState.Failed(name, message)
                ),
        )
        mutableEvents.tryEmit(RootfsEvent.Message(message))
    }

    fun close() {
        cancelled += mutableState.value.downloads.keys
        executorService?.shutdownNow()
    }

    private companion object {
        fun deviceArch(): String {
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            return when {
                abi.startsWith("arm64") -> "arm64"
                abi.startsWith("x86_64") -> "amd64"
                abi.startsWith("armeabi") -> "armhf"
                else -> "arm64"
            }
        }
    }
}
