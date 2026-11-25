package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.trierarch.core.ProfileService
import app.trierarch.core.SessionPhase
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.ProfilesState

@Composable
internal fun ProfileDetailsScreen(
    profiles: ProfilesState,
    id: String,
    error: String?,
    profilesController: ProfilesController,
) {
    val document = profiles.documents.firstOrNull { it.id == id }
    if (document == null) {
        LaunchedEffect(id) { profilesController.back() }
        return
    }
    var showDelete by remember(id) { mutableStateOf(false) }
    val session = profiles.sessions.sessions[id]
    val busy = profiles.sessions.pending != null

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(document.id) {
            HeaderAction("Back") { profilesController.back() }
        }
        ErrorBanner(error)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("PROFILE")
            InfoRow("runtime", document.runtime.ifEmpty { "—" })
            InfoRow(
                "display",
                if (document.display == ProfileService.DISPLAY_NONE) "terminal" else document.display,
            )
            InfoRow("renderer", document.renderer ?: "—")
            InfoRow("state", phaseText(session))
            document.error?.let { message ->
                Text(
                    text = message,
                    color = GuiColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (session?.phase ?: SessionPhase.STOPPED) {
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
            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextAction("Edit") { profilesController.openEditor(document.id) }
                TextAction("Logs") { profilesController.openLogs(document.id) }
                TextAction("Delete", GuiColors.Error) { showDelete = true }
            }
        }
    }

    if (showDelete) {
        DeleteProfileDialog(
            document = document,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                profilesController.delete(document.id)
            },
        )
    }
}
