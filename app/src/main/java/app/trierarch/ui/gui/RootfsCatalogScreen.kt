package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.trierarch.feature.rootfs.DownloadedRootfs
import app.trierarch.feature.rootfs.InstalledRootfs
import app.trierarch.feature.rootfs.LxcImage
import app.trierarch.feature.rootfs.RootfsController
import app.trierarch.feature.rootfs.RootfsState

@Composable
internal fun RootfsCatalogScreen(
    state: RootfsState,
    controller: RootfsController,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var extractTarget by remember { mutableStateOf<Pair<LxcImage, DownloadedRootfs>?>(null) }
    var renameTarget by remember { mutableStateOf<InstalledRootfs?>(null) }
    var deleteTarget by remember { mutableStateOf<InstalledRootfs?>(null) }
    val filtered = remember(state.images, query) {
        val text = query.trim()
        if (text.isEmpty()) {
            state.images
        } else {
            state.images.filter { image ->
                image.title.contains(text, ignoreCase = true) ||
                    image.product.contains(text, ignoreCase = true) ||
                    image.release.contains(text, ignoreCase = true)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Rootfs") {
            HeaderAction("Refresh", enabled = !state.loading) {
                controller.load(true)
                controller.refreshInstalled()
            }
            HeaderAction("Back", onClick = onBack)
        }
        Text(
            text = "Images from Linux Containers; Trierarch does not host them.",
            color = GuiColors.Secondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search distribution") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        ErrorBanner(state.error)
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            item {
                InstalledSection(
                    installed = state.installed,
                    onRename = { renameTarget = it },
                    onDelete = { deleteTarget = it },
                )
            }
            item { SectionLabel("AVAILABLE · ${filtered.size}") }
            when {
                state.loading && state.images.isEmpty() -> item {
                    Text(
                        text = "Loading images…",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                state.images.isEmpty() -> item {
                    Column {
                        Text(
                            text = "No images loaded.",
                            color = GuiColors.Secondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        PrimaryAction("Retry") { controller.load(true) }
                    }
                }
                filtered.isEmpty() -> item {
                    Text(
                        text = "No distribution matches \"$query\".",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                else -> items(filtered, key = { it.product }) { image ->
                    RootfsRow(image, state, controller) { targetImage, downloaded ->
                        extractTarget = targetImage to downloaded
                    }
                    GuiDivider()
                }
            }
        }
    }

    renameTarget?.let { entry ->
        RenameRootfsDialog(
            currentName = entry.name,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                renameTarget = null
                controller.renameInstalled(entry, name)
            },
        )
    }

    deleteTarget?.let { entry ->
        DeleteRootfsDialog(
            entry = entry,
            onDismiss = { deleteTarget = null },
            onDelete = {
                deleteTarget = null
                controller.deleteInstalled(entry)
            },
        )
    }

    extractTarget?.let { (image, downloaded) ->
        ExtractRootfsDialog(
            archive = downloaded.file,
            suggestedName = image.defaultRootfsName,
            onDismiss = { extractTarget = null },
            onExtract = { name ->
                extractTarget = null
                controller.install(downloaded, name)
            },
        )
    }
}

@Composable
private fun InstalledSection(
    installed: List<InstalledRootfs>,
    onRename: (InstalledRootfs) -> Unit,
    onDelete: (InstalledRootfs) -> Unit,
) {
    Column {
        SectionLabel("INSTALLED · ${installed.size}")
        Text(
            text = "files/rootfs",
            color = GuiColors.Secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (installed.isEmpty()) {
            Text(
                text = "No rootfs in files/rootfs.",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        } else {
            installed.forEach { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = entry.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = entry.path,
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row {
                        TextAction("Rename") { onRename(entry) }
                        TextAction("Delete", GuiColors.Error) { onDelete(entry) }
                    }
                }
                GuiDivider()
            }
        }
    }
}

@Composable
private fun RootfsRow(
    image: LxcImage,
    state: RootfsState,
    controller: RootfsController,
    onExtract: (LxcImage, DownloadedRootfs) -> Unit,
) {
    val downloaded = state.downloaded.firstOrNull {
        it.product == image.product || it.file == image.fileName
    }
    val download = state.downloads[image.product]
    val install = downloaded?.let { state.installs[it.file] }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(image.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${image.variant} · ${image.arch} · " +
                        "${buildDateText(image.buildDate)} · ${megabytes(image.size)}",
                    color = GuiColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (image.recommended) {
                    Text(
                        text = "Recommended",
                        color = GuiColors.Pending,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                downloaded?.let {
                    Text(
                        text = "files/downloads/rootfs/${it.file}",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                when (install) {
                    is RootfsState.InstallState.Installing -> Text(
                        text = "Extracting to files/rootfs/${install.name}…",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    is RootfsState.InstallState.Installed -> Text(
                        text = "Extracted to files/rootfs/${install.name}",
                        color = GuiColors.Running,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    is RootfsState.InstallState.Failed -> Text(
                        text = install.message,
                        color = GuiColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    null -> Unit
                }
            }
            when {
                downloaded != null -> {
                    if (install is RootfsState.InstallState.Installing) {
                        Text(
                            text = "Extracting…",
                            color = GuiColors.Secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        when (install) {
                            is RootfsState.InstallState.Installed -> Text(
                                text = "Extracted",
                                color = GuiColors.Running,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            is RootfsState.InstallState.Failed -> TextAction(
                                "Retry",
                                GuiColors.Pending,
                            ) { onExtract(image, downloaded) }
                            else -> TextAction("Extract", GuiColors.Running) {
                                onExtract(image, downloaded)
                            }
                        }
                        TextAction("Delete archive", GuiColors.Error) {
                            controller.remove(downloaded)
                        }
                    }
                }
                download is RootfsState.DownloadState.Downloading -> {
                    TextAction("Cancel", GuiColors.Error) { controller.cancel(image) }
                }
                download is RootfsState.DownloadState.Failed -> {
                    HeaderAction("Retry") { controller.download(image) }
                }
                else -> {
                    PrimaryAction("Download") { controller.download(image) }
                }
            }
        }
        if (download is RootfsState.DownloadState.Downloading) {
            Text(
                text = downloadText(download.downloaded, download.total),
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (download is RootfsState.DownloadState.Failed) {
            Text(
                text = download.message,
                color = GuiColors.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun buildDateText(value: String): String {
    val date = value.substringBefore('_')
    return if (date.length == 8) {
        "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
    } else {
        value
    }
}
