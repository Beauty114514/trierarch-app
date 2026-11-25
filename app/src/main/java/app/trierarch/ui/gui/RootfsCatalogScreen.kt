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
import androidx.compose.ui.unit.dp
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
            HeaderAction("Refresh", enabled = !state.loading) { controller.load(true) }
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
        when {
            state.loading && state.images.isEmpty() -> Text(
                text = "Loading images…",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            state.images.isEmpty() -> {
                Text(
                    text = "No images loaded.",
                    color = GuiColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                PrimaryAction("Retry") { controller.load(true) }
            }
            filtered.isEmpty() -> Text(
                text = "No distribution matches \"$query\".",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            else -> LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(filtered, key = { it.product }) { image ->
                    RootfsRow(image, state, controller)
                    GuiDivider()
                }
            }
        }
    }
}

@Composable
private fun RootfsRow(
    image: LxcImage,
    state: RootfsState,
    controller: RootfsController,
) {
    val downloaded = state.downloaded.firstOrNull {
        it.product == image.product || it.file == image.fileName
    }
    val download = state.downloads[image.product]

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
                        text = "文档推荐",
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
            }
            when {
                downloaded != null -> {
                    Text(
                        text = "Downloaded",
                        color = GuiColors.Running,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextAction("Delete", GuiColors.Error) { controller.remove(downloaded) }
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
