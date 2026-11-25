package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.trierarch.core.ProfileDocument
import app.trierarch.feature.rootfs.InstalledRootfs
import app.trierarch.feature.rootfs.validateRootfsName
import app.trierarch.update.ComponentRelease

@Composable
internal fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New environment") },
        text = {
            Column {
                Text(
                    "Creates the profile template; edit it before running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuiColors.Secondary,
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = {
                        id = it
                        error = null
                    },
                    label = { Text("profile id") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                error?.let {
                    Text(
                        text = it,
                        color = GuiColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = id.trim()
                if (trimmed.isEmpty()) {
                    error = "profile id is empty"
                } else {
                    onCreate(trimmed)
                    onDismiss()
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ImportProfileDialog(
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import rootfs") },
        text = {
            Column {
                Text(
                    "Choose a .tar.xz archive next. The name becomes files/rootfs/<name>.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuiColors.Secondary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("rootfs name") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                error?.let {
                    Text(
                        text = it,
                        color = GuiColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "rootfs name is empty"
                } else {
                    onChoose(trimmed)
                    onDismiss()
                }
            }) { Text("Choose archive") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun DeleteProfileDialog(
    document: ProfileDocument,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${document.id}?") },
        text = {
            Text(
                "The profile file is removed. A running session is not stopped.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = GuiColors.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun DeleteComponentDialog(
    component: ComponentRelease,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${component.name}?") },
        text = {
            Text(
                "Removes files/components/${component.file}. " +
                    "A copy already placed in a rootfs is not touched.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = GuiColors.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ExtractRootfsDialog(
    archive: String,
    suggestedName: String,
    onDismiss: () -> Unit,
    onExtract: (String) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extract rootfs") },
        text = {
            Column {
                Text(
                    "Extracts a copy to files/rootfs/<name>. " +
                        "The archive $archive stays in files/downloads/rootfs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuiColors.Secondary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("rootfs name") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                error?.let {
                    Text(
                        text = it,
                        color = GuiColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                val invalid = validateRootfsName(trimmed)
                if (invalid != null) {
                    error = invalid
                } else {
                    onExtract(trimmed)
                    onDismiss()
                }
            }) { Text("Extract") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun RenameRootfsDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename rootfs") },
        text = {
            Column {
                Text(
                    "Current name: $currentName",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuiColors.Secondary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("new name") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                error?.let {
                    Text(
                        text = it,
                        color = GuiColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                val invalid = validateRootfsName(trimmed)
                if (invalid != null) {
                    error = invalid
                } else {
                    onRename(trimmed)
                    onDismiss()
                }
            }) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun DeleteRootfsDialog(
    entry: InstalledRootfs,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${entry.name}?") },
        text = {
            Text(
                "Removes ${entry.path}. Profiles that reference it are checked first.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = GuiColors.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
