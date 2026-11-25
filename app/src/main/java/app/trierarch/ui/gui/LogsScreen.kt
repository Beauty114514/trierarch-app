package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.ProfilesState

@Composable
internal fun LogsScreen(
    profiles: ProfilesState,
    id: String,
    error: String?,
    profilesController: ProfilesController,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Logs · $id") {
            HeaderAction("Refresh") { profilesController.openLogs(id) }
            HeaderAction("Back") { profilesController.back() }
        }
        ErrorBanner(error)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SelectionContainer {
                Text(
                    text = profiles.logText ?: "No session log yet.",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}
