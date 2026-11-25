package app.trierarch.ui.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trierarch.core.ProfileDocument
import app.trierarch.core.ProfileService
import app.trierarch.core.SessionPhase
import app.trierarch.core.SessionSnapshot
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.ProfilesState
import app.trierarch.feature.updates.UpdatesState

@Composable
internal fun ProfilesListScreen(
    profiles: ProfilesState,
    updates: UpdatesState,
    error: String?,
    profilesController: ProfilesController,
    onTerminal: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenRootfs: () -> Unit,
    onPickArchive: (String) -> Unit,
    onCreate: () -> Unit,
) {
    var showImport by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Trierarch") {
            HeaderAction("Create", onClick = onCreate)
            HeaderAction("Import") { showImport = true }
            HeaderAction("Terminal", onClick = onTerminal)
        }
        ErrorBanner(error)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            UpdatesEntry(updates, onOpenUpdates)
            RootfsEntry(onOpenRootfs)
            SectionLabel("ENVIRONMENTS · ${profiles.documents.size}")
            if (profiles.documents.isEmpty()) {
                EmptyState()
            } else {
                val busy = profiles.sessions.pending != null
                profiles.documents.forEach { document ->
                    EnvironmentRow(
                        document = document,
                        session = profiles.sessions.sessions[document.id],
                        busy = busy,
                        profilesController = profilesController,
                    )
                    GuiDivider()
                }
            }
        }
    }

    if (showImport) {
        ImportProfileDialog(
            onDismiss = { showImport = false },
            onChoose = { name ->
                showImport = false
                onPickArchive(name)
            },
        )
    }
}

@Composable
private fun UpdatesEntry(updates: UpdatesState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Text(
                text = updates.summaryText(),
                color = if (updates.hasAvailableUpdate()) GuiColors.Pending else GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text("›", color = GuiColors.Secondary, fontSize = 22.sp)
    }
    GuiDivider()
}

@Composable
private fun RootfsEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Rootfs", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Download distro images from Linux Containers",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text("›", color = GuiColors.Secondary, fontSize = 22.sp)
    }
    GuiDivider()
}

@Composable
private fun EnvironmentRow(
    document: ProfileDocument,
    session: SessionSnapshot?,
    busy: Boolean,
    profilesController: ProfilesController,
) {
    val phase = session?.phase ?: SessionPhase.STOPPED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { profilesController.openDetails(document.id) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = document.id,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
            )
            if (document.error == null) {
                val display = if (document.display == ProfileService.DISPLAY_NONE) {
                    "terminal"
                } else {
                    document.display
                }
                val badges = listOfNotNull(document.runtime, display, document.renderer)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · ")
                if (badges.isNotEmpty()) {
                    Text(
                        text = badges,
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text(
                text = phaseText(session) + if (session?.active == true) " · shown" else "",
                color = when (phase) {
                    SessionPhase.STARTING, SessionPhase.STOPPING -> GuiColors.Pending
                    SessionPhase.RUNNING -> GuiColors.Running
                    SessionPhase.STOPPED -> GuiColors.Secondary
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            document.error?.let { message ->
                Text(
                    text = message,
                    color = GuiColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (document.error == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (phase) {
                    SessionPhase.STOPPED -> PrimaryAction("Start", !busy) {
                        profilesController.run(document.id)
                    }
                    SessionPhase.RUNNING -> {
                        PrimaryAction("Open", !busy) {
                            profilesController.open(document.id)
                        }
                        TextAction("Stop", GuiColors.Error) {
                            profilesController.stop(document.id)
                        }
                    }
                    SessionPhase.STARTING, SessionPhase.STOPPING -> PrimaryAction("…", false) {}
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "No environments yet.\n\nUse Create to add one, or from the terminal:\n\n" +
            "  trierarch --create arch-demo\n" +
            "  trierarch --config arch-demo",
        color = GuiColors.Secondary,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 16.dp),
    )
}
