package app.trierarch.ui.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ScreenHeader(
    title: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            content = actions,
        )
    }
}

@Composable
internal fun HeaderAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun PrimaryAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = Modifier.height(40.dp),
    ) {
        Text(label)
    }
}

@Composable
internal fun TextAction(
    label: String,
    color: Color = GuiColors.Secondary,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = GuiColors.Secondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            color = GuiColors.Secondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
internal fun ErrorBanner(message: String?) {
    if (message == null) return
    Text(
        text = message,
        color = GuiColors.Error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun GuiDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GuiColors.Divider),
    )
}

@Composable
internal fun StatusLine(message: String, color: Color = GuiColors.Secondary) {
    Text(
        text = message,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}
