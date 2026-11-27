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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.trierarch.feature.components.FileComponentEntry
import app.trierarch.feature.components.FileComponentStore
import app.trierarch.feature.components.FileComponentsController
import app.trierarch.feature.components.PacmanTarget
import app.trierarch.feature.components.PacmanTransaction
import app.trierarch.update.ComponentInstallType
import app.trierarch.update.ComponentRelease
import app.trierarch.update.ComponentStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A file browser for <rootfs>/opt/trierarch/components. There is deliberately
 * no component manifest here: installing adds archive children, listing reads
 * the directory, and deleting removes the chosen entry.
 */
@Composable
internal fun RootfsComponentsScreen(
    target: ComponentsTarget,
    packageComponents: List<ComponentStatus>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val rootfs = (target as? ComponentsTarget.AppRootfs)?.rootfs
    val store = remember { FileComponentStore(File(context.filesDir, "components")) }
    val controller = remember(target) {
        rootfs?.let { FileComponentsController(store, File(it.path)) }
    }
    val state by (controller?.state ?: kotlinx.coroutines.flow.MutableStateFlow(
        app.trierarch.feature.components.FileComponentsState(),
    )).collectAsState()
    var pacmanComponent by remember { mutableStateOf<ComponentRelease?>(null) }
    var installedPackages by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var checkingPackages by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun targetEnvironment(): PacmanTarget? = when (target) {
        is ComponentsTarget.DroidSpaces -> PacmanTarget.DroidSpaces(
            displayName = "DroidSpaces · ${target.container}",
            container = target.container,
            cacheDirectory = context.cacheDir,
        )
        is ComponentsTarget.AppRootfs -> PacmanTarget.Chroot(
            displayName = "chroot · ${target.rootfs.name}",
            rootfs = File(target.rootfs.path),
        )
    }

    fun checkInstalled(component: ComponentRelease, pacmanTarget: PacmanTarget? = targetEnvironment()) {
        if (pacmanTarget == null || checkingPackages != null) return
        checkingPackages = component.id
        scope.launch {
            val versions = withContext(Dispatchers.IO) {
                runCatching {
                    PacmanTransaction(pacmanTarget.environment())
                        .query(component.install.packages)
                }.getOrDefault(emptyMap())
            }
            installedPackages = installedPackages + (component.id to versions)
            checkingPackages = null
        }
    }

    fun uninstall(component: ComponentRelease) {
        val pacmanTarget = targetEnvironment() ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PacmanTransaction(pacmanTarget.environment())
                        .uninstall(component.install.packages)
                }
            }
            installedPackages = installedPackages - component.id
            val message = result.fold(
                onSuccess = { "Uninstalled ${component.name}" },
                onFailure = { "Uninstall failed: ${it.message}" },
            )
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(target) { controller?.load() }
    DisposableEffect(controller) { onDispose { controller?.close() } }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Components") { HeaderAction("Back", onClick = onBack) }
        Text(
            text = when (target) {
                is ComponentsTarget.AppRootfs -> target.rootfs.name
                is ComponentsTarget.DroidSpaces -> target.container
            },
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = when (target) {
                is ComponentsTarget.AppRootfs -> "app rootfs · opt/trierarch/components"
                is ComponentsTarget.DroidSpaces -> "DroidSpaces container · pacman packages"
            },
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

        if (rootfs == null) {
            SectionLabel("FILES")
            Text(
                text = "File components are only available for app rootfs. " +
                    "Use Rootfs > Components for a DroidSpaces container to install packages.",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
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
                        onDelete = { controller?.delete(entry) },
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
                        ) { controller?.install(archive) }
                    }
                    GuiDivider()
                }
            }
        }

        SectionLabel("PACMAN COMPONENTS")
        val pacman = packageComponents.filter {
            it.release.install.type == ComponentInstallType.PACMAN &&
                it.state == app.trierarch.update.ComponentState.UP_TO_DATE
        }
        if (pacman.isEmpty()) {
            Text(
                text = "No downloaded pacman components. Download them from Updates first.",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            pacman.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(status.release.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = status.release.install.packages.joinToString(", "),
                            color = GuiColors.Secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val versions = installedPackages[status.release.id].orEmpty()
                    val installed = status.release.install.packages.all { versions.containsKey(it) }
                    if (installed) {
                        Column(Modifier.padding(start = 8.dp)) {
                            StatusLine(
                                versions.entries.joinToString(", ") { "${it.key} ${it.value}" },
                                GuiColors.Running,
                            )
                            TextAction("Uninstall", GuiColors.Error) { uninstall(status.release) }
                        }
                    } else {
                        Column(Modifier.padding(start = 8.dp)) {
                            PrimaryAction("Install packages") { pacmanComponent = status.release }
                            TextAction("Check installed") { checkInstalled(status.release) }
                        }
                    }
                }
                GuiDivider()
            }
        }
    }

    pacmanComponent?.let { component ->
        val rootfsEntry = (target as? ComponentsTarget.AppRootfs)?.rootfs
        PacmanInstallDialog(
            component = component,
            installedRootfs = listOfNotNull(rootfsEntry),
            droidspacesContainers = if (target is ComponentsTarget.DroidSpaces) {
                listOf(target.container)
            } else {
                emptyList()
            },
            onDismiss = { pacmanComponent = null },
            onInstall = { pacmanTarget ->
                pacmanComponent = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            PacmanTransaction(pacmanTarget.environment()).install(
                                File(context.filesDir, "components/${component.file}"),
                                component.install.packages,
                            )
                        }
                    }
                    val message = result.fold(
                        onSuccess = { "Installed ${component.name}" },
                        onFailure = { "Install failed: ${it.message}" },
                    )
                    if (result.isSuccess) checkInstalled(component, pacmanTarget)
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            },
        )
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
