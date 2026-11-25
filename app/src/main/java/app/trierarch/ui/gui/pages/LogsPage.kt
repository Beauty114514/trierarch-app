package app.trierarch.ui.gui.pages

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.trierarch.ui.gui.GuiHost

internal object LogsPage {
    fun render(host: GuiHost, id: String) {
        val style = host.style
        host.header.addView(
            style.title("Logs · $id"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        host.header.addView(style.headerButton("Refresh") { host.profiles.openLogs(id) })
        host.header.addView(style.headerButton("Back") { host.profiles.back() })
        host.content.addView(TextView(host.context).apply {
            setText(host.profilesState.logText ?: "No session log yet.")
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, style.dp(14), 0, 0)
        })
    }
}
