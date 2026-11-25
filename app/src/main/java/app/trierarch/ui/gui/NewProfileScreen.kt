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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.trierarch.feature.profiles.ProfileDraft
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.validateProfileDraft
import app.trierarch.feature.rootfs.InstalledRootfs

private enum class RuntimeChoice(val label: String, val subtitle: String) {
    PROOT("PRoot", "No root required"),
    CHROOT("chroot", "Requires root"),
    DROIDSPACES("DroidSpaces", "Existing container"),
}

private enum class DisplayChoice(val label: String, val value: String) {
    TERMINAL("Terminal", ProfileDraft.DISPLAY_TERMINAL),
    X11("X11", ProfileDraft.DISPLAY_X11),
    WAYLAND("Wayland", ProfileDraft.DISPLAY_WAYLAND),
}

@Composable
internal fun NewProfileScreen(
    profilesController: ProfilesController,
    profilesError: String?,
    installedRootfs: List<InstalledRootfs>,
    onBack: () -> Unit,
    onOpenRootfs: () -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var runtime by remember { mutableStateOf(RuntimeChoice.PROOT) }
    var rootfsPath by remember { mutableStateOf("") }
    var shell by remember { mutableStateOf("/bin/sh") }
    var container by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("root") }
    var display by remember { mutableStateOf(DisplayChoice.TERMINAL) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val profiles by profilesController.state.collectAsState()

    LaunchedEffect(installedRootfs) {
        if (rootfsPath.isEmpty() || installedRootfs.none { it.path == rootfsPath }) {
            rootfsPath = installedRootfs.firstOrNull()?.path.orEmpty()
        }
    }
    LaunchedEffect(profilesError) {
        if (profilesError != null) creating = false
    }
    LaunchedEffect(runtime) {
        if (runtime == RuntimeChoice.DROIDSPACES &&
            profiles.droidspacesContainers.isEmpty() &&
            !profiles.droidspacesLoading
        ) {
            profilesController.refreshDroidspacesContainers()
        }
    }
    LaunchedEffect(profiles.droidspacesContainers) {
        if (container.isBlank() && profiles.droidspacesContainers.isNotEmpty()) {
            container = profiles.droidspacesContainers.first()
        }
    }

    fun draft(): ProfileDraft = when (runtime) {
        RuntimeChoice.PROOT -> ProfileDraft.Proot(
            id = id.trim(),
            rootfs = rootfsPath,
            shell = shell.trim(),
            display = display.value,
        )
        RuntimeChoice.CHROOT -> ProfileDraft.Chroot(
            id = id.trim(),
            rootfs = rootfsPath,
            shell = shell.trim(),
            display = display.value,
        )
        RuntimeChoice.DROIDSPACES -> ProfileDraft.Droidspaces(
            id = id.trim(),
            container = container.trim(),
            user = user.trim(),
            display = display.value,
        )
    }

    fun create() {
        val candidate = draft()
        val invalid = validateProfileDraft(candidate)
        if (invalid != null) {
            error = invalid
            return
        }
        error = null
        creating = true
        profilesController.create(candidate)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("New profile") {
            HeaderAction("Create", enabled = !creating) { create() }
            HeaderAction("Cancel", onClick = onBack)
        }
        ErrorBanner(error ?: profilesError)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = id,
                onValueChange = {
                    id = it
                    error = null
                },
                label = { Text("profile id") },
                singleLine = true,
                isError = error != null,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            SectionLabel("RUNTIME")
            RuntimeChoice.entries.forEach { choice ->
                ChoiceRow(
                    selected = runtime == choice,
                    title = choice.label,
                    subtitle = choice.subtitle,
                ) { runtime = choice }
            }

            if (runtime == RuntimeChoice.DROIDSPACES) {
                SectionLabel("CONTAINER")
                when {
                    profiles.droidspacesLoading -> Text(
                        text = "Detecting containers…",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    profiles.droidspacesError != null -> {
                        Text(
                            text = profiles.droidspacesError!!,
                            color = GuiColors.Error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        HeaderAction("Retry") {
                            profilesController.refreshDroidspacesContainers()
                        }
                    }
                    profiles.droidspacesContainers.isEmpty() -> {
                        Text(
                            text = "No DroidSpaces containers detected.",
                            color = GuiColors.Secondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        HeaderAction("Refresh") {
                            profilesController.refreshDroidspacesContainers()
                        }
                    }
                    else -> {
                        Text(
                            text = "Detected on this device:",
                            color = GuiColors.Secondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        profiles.droidspacesContainers.forEach { name ->
                            ChoiceRow(
                                selected = container == name,
                                title = name,
                                subtitle = null,
                            ) { container = name }
                        }
                        HeaderAction("Refresh") {
                            profilesController.refreshDroidspacesContainers()
                        }
                    }
                }
                OutlinedTextField(
                    value = user,
                    onValueChange = {
                        user = it
                        error = null
                    },
                    label = { Text("user") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    text = "The container must already exist in DroidSpaces; Trierarch only starts it.",
                    color = GuiColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                SectionLabel("ROOTFS")
                if (installedRootfs.isEmpty()) {
                    Text(
                        text = "No rootfs installed in files/rootfs.",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    PrimaryAction("Open Rootfs") { onOpenRootfs() }
                } else {
                    installedRootfs.forEach { entry ->
                        ChoiceRow(
                            selected = rootfsPath == entry.path,
                            title = entry.name,
                            subtitle = entry.path,
                        ) { rootfsPath = entry.path }
                    }
                }
                OutlinedTextField(
                    value = shell,
                    onValueChange = {
                        shell = it
                        error = null
                    },
                    label = { Text("shell") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (runtime == RuntimeChoice.CHROOT) {
                    Text(
                        text = "chroot requires root and a working su provider.",
                        color = GuiColors.Secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            SectionLabel("DISPLAY")
            DisplayChoice.entries.forEach { choice ->
                ChoiceRow(
                    selected = display == choice,
                    title = choice.label,
                    subtitle = null,
                ) { display = choice }
            }

            Text(
                text = "Advanced graphics/audio/launch fields remain editable in the TOML editor.",
                color = GuiColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    text = it,
                    color = GuiColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
