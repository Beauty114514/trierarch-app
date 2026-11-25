package app.trierarch.ui.gui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.trierarch.feature.profiles.ProfileScreen
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.ProfilesEvent
import app.trierarch.feature.updates.UpdateEvent
import app.trierarch.feature.updates.UpdatesState
import app.trierarch.feature.updates.UpdatesViewModel

/**
 * The Compose host. It owns the ComposeView and the two pieces of UI state
 * (Updates open, IME inset); the feature layer owns everything else.
 */
internal class GuiScreen(
    context: Context,
    private val profilesController: ProfilesController,
    private val updatesViewModel: UpdatesViewModel,
    private val onClose: () -> Unit,
    private val onFollowSession: () -> Unit,
    private val onPickArchive: (String) -> Unit,
) {
    private var showUpdates by mutableStateOf(false)
    private var imeBottomInset by mutableIntStateOf(0)

    private val composeView = ComposeView(context).apply {
        setContent {
            TrierarchTheme {
                GuiRoot(
                    profilesController = profilesController,
                    updatesViewModel = updatesViewModel,
                    showUpdates = showUpdates,
                    imeInset = imeBottomInset,
                    onFollowSession = onFollowSession,
                    onClose = onClose,
                    onPickArchive = onPickArchive,
                    onOpenUpdates = {
                        showUpdates = true
                        if (updatesViewModel.state.value.phase == UpdatesState.Phase.NOT_CHECKED) {
                            updatesViewModel.check()
                        }
                    },
                    onCloseUpdates = { showUpdates = false },
                )
            }
        }
    }

    val view: View get() = composeView

    fun setImeInset(inset: Int) {
        imeBottomInset = inset
    }

    fun refresh() {
        profilesController.refresh()
    }

    fun handleBack(): Boolean {
        if (showUpdates) {
            showUpdates = false
            return true
        }
        return profilesController.back()
    }

    fun release() {
        composeView.disposeComposition()
    }
}

@Composable
private fun GuiRoot(
    profilesController: ProfilesController,
    updatesViewModel: UpdatesViewModel,
    showUpdates: Boolean,
    imeInset: Int,
    onFollowSession: () -> Unit,
    onClose: () -> Unit,
    onPickArchive: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onCloseUpdates: () -> Unit,
) {
    val context = LocalContext.current
    val profiles by profilesController.state.collectAsState()
    val updates by updatesViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        profilesController.events.collect { event ->
            when (event) {
                ProfilesEvent.FollowSession -> onFollowSession()
                is ProfilesEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(Unit) {
        updatesViewModel.events.collect { event ->
            when (event) {
                is UpdateEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    val imeDp = with(LocalDensity.current) { imeInset.toDp() }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 16.dp + imeDp,
                ),
        ) {
            if (showUpdates) {
                UpdatesScreen(
                    updates = updates,
                    updatesViewModel = updatesViewModel,
                    onBack = onCloseUpdates,
                )
            } else {
                when (val screen = profiles.screen) {
                    ProfileScreen.List -> ProfilesListScreen(
                        profiles = profiles,
                        updates = updates,
                        error = profiles.error,
                        profilesController = profilesController,
                        onTerminal = onClose,
                        onOpenUpdates = onOpenUpdates,
                        onPickArchive = onPickArchive,
                    )
                    is ProfileScreen.Details -> ProfileDetailsScreen(
                        profiles = profiles,
                        id = screen.id,
                        error = profiles.error,
                        profilesController = profilesController,
                    )
                    is ProfileScreen.Editor -> ProfileEditorScreen(
                        profiles = profiles,
                        id = screen.id,
                        error = profiles.error,
                        profilesController = profilesController,
                    )
                    is ProfileScreen.Logs -> LogsScreen(
                        profiles = profiles,
                        id = screen.id,
                        error = profiles.error,
                        profilesController = profilesController,
                    )
                }
            }
        }
    }
}
