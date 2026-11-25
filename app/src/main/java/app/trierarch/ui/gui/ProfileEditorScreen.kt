package app.trierarch.ui.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.ProfilesState

@Composable
internal fun ProfileEditorScreen(
    profiles: ProfilesState,
    id: String,
    error: String?,
    profilesController: ProfilesController,
) {
    var text by remember(id) { mutableStateOf(profiles.editorText) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Edit $id") {
            HeaderAction("Save") { profilesController.saveEditor(text) }
            HeaderAction("Cancel") { profilesController.back() }
        }
        ErrorBanner(error)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = GuiColors.Running,
                focusedContainerColor = GuiColors.Field,
                unfocusedContainerColor = GuiColors.Field,
                focusedBorderColor = GuiColors.Running,
                unfocusedBorderColor = GuiColors.Divider,
            ),
        )
        Text(
            text = "Raw TOML. Save checks that the file has not changed since it was opened.",
            color = GuiColors.Secondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
