package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.trierarch.feature.updates.UpdatesState
import app.trierarch.feature.updates.UpdatesViewModel
import app.trierarch.update.AppState
import app.trierarch.update.ComponentRelease
import app.trierarch.update.ComponentState
import app.trierarch.update.ComponentStatus
import java.util.Date

@Composable
internal fun UpdatesScreen(
    updates: UpdatesState,
    updatesViewModel: UpdatesViewModel,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ComponentRelease?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Updates") {
            HeaderAction(
                label = if (updates.phase == UpdatesState.Phase.CHECKING) "Checking…" else "Check",
                enabled = updates.phase != UpdatesState.Phase.CHECKING,
            ) {
                updatesViewModel.check()
            }
            HeaderAction("Back", onClick = onBack)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("APP")
            InfoRow(
                "current",
                "${updates.installedVersionName} (${updates.installedVersionCode})",
            )
            when (updates.phase) {
                UpdatesState.Phase.NOT_CHECKED -> InfoRow("latest", "not checked")
                UpdatesState.Phase.CHECKING -> InfoRow("latest", "checking…")
                UpdatesState.Phase.FAILED -> Text(
                    text = updates.error ?: "update check failed",
                    color = GuiColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
                UpdatesState.Phase.READY -> {
                    val manifest = updates.manifest
                    val status = updates.status
                    if (manifest != null && status != null) {
                        InfoRow(
                            "latest",
                            "${manifest.app.versionName} (${manifest.app.versionCode})",
                        )
                        val appState = status.app.state
                        StatusLine(
                            message = if (appState == AppState.UPDATE_AVAILABLE) {
                                "Update available"
                            } else {
                                "Up to date"
                            },
                            color = if (appState == AppState.UPDATE_AVAILABLE) {
                                GuiColors.Pending
                            } else {
                                GuiColors.Running
                            },
                        )
                        if (appState == AppState.UPDATE_AVAILABLE) {
                            ApkControls(updates, updatesViewModel)
                        }
                        SectionLabel("COMPONENTS")
                        status.components.forEach { componentStatus ->
                            ComponentRow(
                                updates = updates,
                                status = componentStatus,
                                updatesViewModel = updatesViewModel,
                                onDelete = { pendingDelete = it },
                            )
                        }
                        if (updates.checkedAt > 0) {
                            Text(
                                text = "Last checked: ${checkedAtText(updates.checkedAt)}",
                                color = GuiColors.Secondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { component ->
        DeleteComponentDialog(
            component = component,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                updatesViewModel.deleteComponent(component)
            },
        )
    }
}

@Composable
private fun ApkControls(updates: UpdatesState, updatesViewModel: UpdatesViewModel) {
    val app = updates.manifest?.app ?: return
    Column(Modifier.padding(top = 10.dp)) {
        when (val apk = updates.apk) {
            UpdatesState.ApkState.Idle -> {
                PrimaryAction("Download") { updatesViewModel.downloadApk() }
                Text(
                    text = "${megabytes(app.apkSize)} · ${app.versionName}",
                    color = GuiColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            is UpdatesState.ApkState.Downloading -> StatusLine(
                downloadText(apk.downloaded, apk.total),
            )
            UpdatesState.ApkState.Verifying -> StatusLine("Verifying…")
            is UpdatesState.ApkState.Ready -> {
                PrimaryAction("Install") { updatesViewModel.installApk() }
                StatusLine("Downloaded ${apk.versionName}", GuiColors.Running)
            }
            is UpdatesState.ApkState.Failed -> {
                StatusLine(apk.message, GuiColors.Error)
                HeaderAction("Retry") { updatesViewModel.downloadApk() }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    updates: UpdatesState,
    status: ComponentStatus,
    updatesViewModel: UpdatesViewModel,
    onDelete: (ComponentRelease) -> Unit,
) {
    val component = status.release
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            text = component.name + (component.version?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        when (val download = updates.components[component.id]) {
            is UpdatesState.ComponentDownload.Downloading -> StatusLine(
                downloadText(download.downloaded, download.total),
            )
            UpdatesState.ComponentDownload.Verifying -> StatusLine("Verifying…")
            is UpdatesState.ComponentDownload.Failed -> {
                StatusLine(download.message, GuiColors.Error)
                HeaderAction("Retry") { updatesViewModel.downloadComponent(component) }
            }
            null -> when (status.state) {
                ComponentState.NOT_DOWNLOADED, ComponentState.UPDATE_AVAILABLE -> {
                    StatusLine(
                        message = componentStateText(status.state),
                        color = if (status.state == ComponentState.UPDATE_AVAILABLE) {
                            GuiColors.Pending
                        } else {
                            GuiColors.Secondary
                        },
                    )
                    HeaderAction("Download") { updatesViewModel.downloadComponent(component) }
                }
                ComponentState.UP_TO_DATE -> {
                    StatusLine("Downloaded", GuiColors.Running)
                    Text(
                        text = "files/components/${component.file}",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    TextAction("Delete", GuiColors.Error) { onDelete(component) }
                }
            }
        }
    }
}

@Composable
private fun checkedAtText(millis: Long): String {
    val context = LocalContext.current
    return remember(context, millis) {
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))
    }
}
