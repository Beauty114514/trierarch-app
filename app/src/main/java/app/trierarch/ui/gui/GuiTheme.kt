package app.trierarch.ui.gui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal object GuiColors {
    val Secondary = Color(0xFF9CA3AF)
    val Divider = Color(0xFF23262B)
    val Running = Color(0xFF7CE38B)
    val Pending = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val Field = Color(0xFF111318)
}

private val colors = darkColorScheme(
    primary = GuiColors.Running,
    onPrimary = Color.Black,
    secondary = GuiColors.Secondary,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = GuiColors.Field,
    onSurfaceVariant = GuiColors.Secondary,
    error = GuiColors.Error,
    onError = Color.Black,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, letterSpacing = 0.96.sp),
)

@Composable
internal fun TrierarchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
