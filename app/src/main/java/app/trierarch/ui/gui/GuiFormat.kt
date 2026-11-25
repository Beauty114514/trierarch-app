package app.trierarch.ui.gui

import app.trierarch.core.SessionPhase
import app.trierarch.core.SessionSnapshot
import app.trierarch.feature.updates.UpdatesState
import app.trierarch.update.AppState
import app.trierarch.update.ComponentState
import java.util.Locale

internal fun phaseText(session: SessionSnapshot?): String =
    when (session?.phase ?: SessionPhase.STOPPED) {
        SessionPhase.STOPPED -> "stopped"
        SessionPhase.STARTING -> "starting"
        SessionPhase.RUNNING -> "running"
        SessionPhase.STOPPING -> "stopping"
    }

internal fun componentStateText(state: ComponentState): String = when (state) {
    ComponentState.NOT_DOWNLOADED -> "Not downloaded"
    ComponentState.UP_TO_DATE -> "Downloaded"
    ComponentState.UPDATE_AVAILABLE -> "Update available"
}

internal fun UpdatesState.hasAvailableUpdate(): Boolean {
    val status = status ?: return false
    return status.app.state == AppState.UPDATE_AVAILABLE ||
        status.components.any { it.state == ComponentState.UPDATE_AVAILABLE }
}

internal fun UpdatesState.summaryText(): String = when (phase) {
    UpdatesState.Phase.NOT_CHECKED -> "Check for app and component updates"
    UpdatesState.Phase.CHECKING -> "Checking…"
    UpdatesState.Phase.FAILED -> "Check failed"
    UpdatesState.Phase.READY -> if (hasAvailableUpdate()) "Update available" else "Up to date"
}

internal fun megabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

internal fun downloadText(downloaded: Long, total: Long): String {
    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
    return "Downloading… $percent% (${megabytes(downloaded)} / ${megabytes(total)})"
}
