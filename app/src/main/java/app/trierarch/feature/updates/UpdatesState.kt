package app.trierarch.feature.updates

import app.trierarch.update.Manifest
import app.trierarch.update.UpdateStatus
import java.io.File

/** Everything the Updates screen needs to draw itself. */
data class UpdatesState(
    val phase: Phase = Phase.NOT_CHECKED,
    val manifest: Manifest? = null,
    val status: UpdateStatus? = null,
    val installedVersionCode: Long = 0,
    val installedVersionName: String = "",
    val checkedAt: Long = 0,
    val error: String? = null,
    val apk: ApkState = ApkState.Idle,
    val components: Map<String, ComponentDownload> = emptyMap(),
) {
    enum class Phase { NOT_CHECKED, CHECKING, READY, FAILED }

    sealed interface ApkState {
        data object Idle : ApkState
        data class Downloading(val downloaded: Long, val total: Long) : ApkState
        data object Verifying : ApkState
        data class Ready(val file: File, val versionName: String) : ApkState
        data class Failed(val message: String) : ApkState
    }

    sealed interface ComponentDownload {
        data class Downloading(val downloaded: Long, val total: Long) : ComponentDownload
        data object Verifying : ComponentDownload
        data class Failed(val message: String) : ComponentDownload
    }
}

sealed interface UpdateEvent {
    data class Message(val text: String) : UpdateEvent
}
