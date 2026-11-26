package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.trierarch.feature.components.FileComponentEntry
import app.trierarch.feature.components.FileComponentStore
import app.trierarch.feature.components.FileComponentsController
import app.trierarch.feature.rootfs.InstalledRootfs
import java.io.File

/**
 * A file browser for <rootfs>/opt/trierarch/components. There is deliberately
 * no component manifest here: installing adds archive children, listing reads
 * the directory, and deleting removes the chosen entry.
 */
@Composable
internal fun RootfsComponentsScreen(
    rootfs: InstalledRootfs,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { FileComponentStore(File(context.filesDir, "components")) }
    val controller = remember(rootfs.name) {
        FileComponentsController(store, File(rootfs.path))
    }
    val state by controller.state.collectAsState()

    LaunchedEffect(rootfs.name) { controller.load() }
    DisposableEffect(controller) { onDispose { controller.close() } }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Components") { HeaderAction("Back", onClick = onBack) }
        Text(
            text = rootfs.name,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "opt/trierarch/components",
            color = GuiColors.Secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Delete an old component folder before installing a new version of it.",
            color = GuiColors.Secondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        ErrorBanner(state.error)
        state.message?.let { StatusLine(it, GuiColors.Running) }

        SectionLabel("INSTALLED · ${state.entries.size}")
        if (state.entries.isEmpty()) {
            Text(
                text = "No files in opt/trierarch/components.",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            state.entries.forEach { entry ->
                ComponentEntryRow(
                    entry = entry,
                    busy = state.busy,
                    onDelete = { controller.delete(entry) },
                )
                GuiDivider()
            }
        }

        SectionLabel("AVAILABLE ARCHIVES · ${state.archives.size}")
        if (state.archives.isEmpty()) {
            Text(
                text = "No downloaded files in files/components.",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            state.archives.forEach { archive ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = archive.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = if (archive.name.endsWith(".tar.zst") ||
                                archive.name.endsWith(".tar.xz")
                            ) {
                                "extract into opt/trierarch/components"
                            } else {
                                "copy into opt/trierarch/components"
                            },
                            color = GuiColors.Secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    PrimaryAction(
                        label = "Install",
                        enabled = !state.busy,
                    ) { controller.install(archive) }
                }
                GuiDivider()
            }
        }
    }
}

@Composable
private fun ComponentEntryRow(
    entry: FileComponentEntry,
    busy: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = if (entry.directory) "directory" else "file · ${entry.size} bytes",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextAction("Delete", GuiColors.Error, onClick = onDelete)
    }
}
