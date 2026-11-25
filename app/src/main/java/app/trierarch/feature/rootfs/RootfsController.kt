package app.trierarch.feature.rootfs

import android.content.Context
import android.os.Build
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

data class RootfsState(
    val loading: Boolean = false,
    val images: List<LxcImage> = emptyList(),
    val error: String? = null,
    val downloads: Map<String, DownloadState> = emptyMap(),
    val downloaded: List<DownloadedRootfs> = emptyList(),
    val loadedAt: Long = 0,
) {
    sealed interface DownloadState {
        data class Downloading(val downloaded: Long, val total: Long) : DownloadState
        data class Failed(val message: String) : DownloadState
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
    constructor(context: Context) : this(
        store = RootfsDownloadStore(File(context.filesDir, "downloads/rootfs")),
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

    fun refreshDownloaded() {
        mutableState.value = mutableState.value.copy(downloaded = store.list())
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
