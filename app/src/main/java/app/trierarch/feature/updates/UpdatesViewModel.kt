package app.trierarch.feature.updates

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import app.trierarch.update.ApkInstaller
import app.trierarch.update.ApkVerifier
import app.trierarch.update.AppState
import app.trierarch.update.ComponentRelease
import app.trierarch.update.ComponentStore
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class UpdatesViewModel(application: Application) : AndroidViewModel(application) {
    private val service = UpdatesService(
        components = ComponentStore(File(application.filesDir, "components")),
        cacheDirectory = application.cacheDir,
        installedVersion = {
            val info = application.packageManager.getPackageInfo(application.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            code to (info.versionName ?: "")
        },
        verifyApk = { file, code -> ApkVerifier.verify(application, file, code) },
        installApk = { file -> ApkInstaller.install(application, file) },
    )
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "trierarch-updates").apply { isDaemon = true }
    }
    private val installed = service.installed()
    private val mutableState = MutableStateFlow(
        UpdatesState(
            installedVersionCode = installed.first,
            installedVersionName = installed.second,
        ),
    )
    private val mutableEvents = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 8)

    val state: StateFlow<UpdatesState> = mutableState
    val events: SharedFlow<UpdateEvent> = mutableEvents

    fun check() {
        val current = mutableState.value
        if (current.phase == UpdatesState.Phase.CHECKING) return
        if (current.apk is UpdatesState.ApkState.Downloading ||
            current.apk is UpdatesState.ApkState.Verifying
        ) {
            return
        }
        if (current.components.values.any {
                it is UpdatesState.ComponentDownload.Downloading ||
                    it === UpdatesState.ComponentDownload.Verifying
            }
        ) {
            return
        }
        mutableState.value = current.copy(
            phase = UpdatesState.Phase.CHECKING,
            error = null,
            apk = UpdatesState.ApkState.Idle,
            components = emptyMap(),
        )
        executor.execute {
            runCatching { service.check() }.fold(
                onSuccess = { check ->
                    mutableState.value = mutableState.value.copy(
                        phase = UpdatesState.Phase.READY,
                        manifest = check.manifest,
                        status = check.status,
                        installedVersionCode = check.installedVersionCode,
                        installedVersionName = check.installedVersionName,
                        checkedAt = System.currentTimeMillis(),
                        error = null,
                    )
                },
                onFailure = { error ->
                    mutableState.value = mutableState.value.copy(
                        phase = UpdatesState.Phase.FAILED,
                        error = error.message ?: "update check failed",
                    )
                },
            )
        }
    }

    fun downloadApk() {
        val current = mutableState.value
        val manifest = current.manifest ?: return
        if (current.status?.app?.state != AppState.UPDATE_AVAILABLE) return
        if (current.apk is UpdatesState.ApkState.Downloading ||
            current.apk is UpdatesState.ApkState.Verifying
        ) {
            return
        }
        mutableState.value = current.copy(
            apk = UpdatesState.ApkState.Downloading(0, manifest.app.apkSize),
        )
        executor.execute {
            val result = runCatching {
                service.downloadApk(
                    manifest.app,
                    onProgress = { downloaded, total ->
                        mutableState.value = mutableState.value.copy(
                            apk = UpdatesState.ApkState.Downloading(downloaded, total),
                        )
                    },
                    onVerifying = {
                        mutableState.value = mutableState.value.copy(
                            apk = UpdatesState.ApkState.Verifying,
                        )
                    },
                )
            }
            mutableState.value = mutableState.value.copy(
                apk = result.fold(
                    onSuccess = { file ->
                        UpdatesState.ApkState.Ready(file, manifest.app.versionName)
                    },
                    onFailure = {
                        UpdatesState.ApkState.Failed(it.message ?: "download failed")
                    },
                ),
            )
        }
    }

    fun installApk() {
        val ready = mutableState.value.apk as? UpdatesState.ApkState.Ready ?: return
        runCatching { service.install(ready.file) }
            .onFailure {
                mutableEvents.tryEmit(UpdateEvent.Message(it.message ?: "unable to start the installer"))
            }
    }

    fun downloadComponent(component: ComponentRelease) {
        val current = mutableState.value
        val existing = current.components[component.id]
        if (existing is UpdatesState.ComponentDownload.Downloading ||
            existing === UpdatesState.ComponentDownload.Verifying
        ) {
            return
        }
        mutableState.value = current.copy(
            components = current.components + (
                component.id to UpdatesState.ComponentDownload.Downloading(0, component.size)
                ),
        )
        executor.execute {
            val result = runCatching {
                service.downloadComponent(
                    component,
                    onProgress = { downloaded, total ->
                        mutableState.value = mutableState.value.copy(
                            components = mutableState.value.components + (
                                component.id to UpdatesState.ComponentDownload.Downloading(
                                    downloaded,
                                    total,
                                )
                                ),
                        )
                    },
                    onVerifying = {
                        mutableState.value = mutableState.value.copy(
                            components = mutableState.value.components + (
                                component.id to UpdatesState.ComponentDownload.Verifying
                                ),
                        )
                    },
                )
            }
            mutableState.value = mutableState.value.copy(
                components = mutableState.value.components - component.id,
            )
            if (result.isSuccess) {
                refreshStatus()
                mutableEvents.tryEmit(UpdateEvent.Message("Saved to files/components/${component.file}"))
            } else {
                mutableState.value = mutableState.value.copy(
                    components = mutableState.value.components + (
                        component.id to UpdatesState.ComponentDownload.Failed(
                            result.exceptionOrNull()?.message ?: "download failed",
                        )
                        ),
                )
            }
        }
    }

    fun deleteComponent(component: ComponentRelease) {
        executor.execute {
            val removed = service.deleteComponent(component.id)
            if (removed == null) {
                mutableEvents.tryEmit(UpdateEvent.Message("nothing to delete for ${component.id}"))
                return@execute
            }
            mutableState.value = mutableState.value.copy(
                components = mutableState.value.components - component.id,
            )
            refreshStatus()
            mutableEvents.tryEmit(UpdateEvent.Message("Deleted ${removed.file}"))
        }
    }

    private fun refreshStatus() {
        val current = mutableState.value
        val manifest = current.manifest ?: return
        mutableState.value = current.copy(
            status = service.statusFor(manifest, current.installedVersionCode),
        )
    }

    override fun onCleared() {
        executor.shutdownNow()
        super.onCleared()
    }
}
