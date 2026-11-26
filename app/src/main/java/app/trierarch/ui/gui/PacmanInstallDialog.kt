package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.trierarch.feature.components.PacmanTarget
import app.trierarch.feature.rootfs.InstalledRootfs
import app.trierarch.update.ComponentRelease
import java.io.File

@Composable
internal fun PacmanInstallDialog(
    component: ComponentRelease,
    installedRootfs: List<InstalledRootfs>,
    droidspacesContainers: List<String>,
    onDismiss: () -> Unit,
    onInstall: (PacmanTarget) -> Unit,
) {
    val context = LocalContext.current
    val targets = remember(installedRootfs, droidspacesContainers) {
        buildList {
            installedRootfs.forEach { entry ->
                add(
                    PacmanTarget.Proot(
                        displayName = "PRoot · ${entry.name}",
                        rootfs = File(entry.path),
                        nativeLibraryDirectory = File(context.applicationInfo.nativeLibraryDir),
                        cacheDirectory = context.cacheDir,
                    ),
                )
                add(
                    PacmanTarget.Chroot(
                        displayName = "chroot · ${entry.name}",
                        rootfs = File(entry.path),
                    ),
                )
            }
            droidspacesContainers.forEach { container ->
                add(
                    PacmanTarget.DroidSpaces(
                        displayName = "DroidSpaces · $container",
                        container = container,
                        cacheDirectory = context.cacheDir,
                    ),
                )
            }
        }
    }
    var selected by remember(targets) { mutableStateOf(targets.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install ${component.name}") },
        text = {
            Column {
                Text(
                    text = "pacman packages: ${component.install.packages.joinToString(", ")}",
                    color = GuiColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "The package files are installed into the selected environment. " +
                        "PRoot cannot install packages on a kernel without root-id support. " +
                        "DroidSpaces must be stopped before package files can be bound.",
                    color = GuiColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (targets.isEmpty()) {
                    Text(
                        text = "No PRoot, chroot, or DroidSpaces target is available.",
                        color = GuiColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    targets.forEach { target ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = selected == target,
                                onClick = { selected = target },
                            )
                            Text(
                                text = target.displayName,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onInstall) },
                enabled = selected != null,
            ) { Text("Install") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
