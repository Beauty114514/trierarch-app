package app.trierarch.ui.gui.pages

import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import app.trierarch.core.ProfileService
import app.trierarch.core.SessionPhase
import app.trierarch.ui.gui.GuiDialogs
import app.trierarch.ui.gui.GuiHost
import app.trierarch.ui.gui.phaseText

internal object ProfileDetailsPage {
    fun render(host: GuiHost, id: String) {
        val style = host.style
        val state = host.profilesState
        val document = state.documents.firstOrNull { it.id == id }
        if (document == null) {
            host.profiles.back()
            return
        }
        host.header.addView(
            style.title(document.id),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        host.header.addView(style.headerButton("Back") { host.profiles.back() })

        host.content.addView(style.sectionLabel("PROFILE"))
        host.content.addView(style.info("runtime", document.runtime.ifEmpty { "—" }))
        host.content.addView(
            style.info(
                "display",
                if (document.display == ProfileService.DISPLAY_NONE) {
                    "terminal"
                } else {
                    document.display
                },
            ),
        )
        host.content.addView(style.info("renderer", document.renderer ?: "—"))
        val snapshot = state.sessions
        val session = snapshot.sessions[document.id]
        host.content.addView(style.info("state", phaseText(session)))

        document.error?.let { message ->
            host.content.addView(TextView(host.context).apply {
                setText(message)
                setTextColor(style.errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, style.dp(12), 0, 0)
            })
        }

        val primary = LinearLayout(host.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, style.dp(18), 0, 0)
        }
        val busy = snapshot.pending != null
        when (session?.phase ?: SessionPhase.STOPPED) {
            SessionPhase.STOPPED -> primary.addView(
                style.primaryAction("Start", busy) { host.profiles.run(document.id) },
            )
            SessionPhase.RUNNING -> {
                primary.addView(
                    style.primaryAction("Open", busy) { host.profiles.open(document.id) },
                )
                primary.addView(
                    style.secondaryAction("Stop", busy) { host.profiles.stop(document.id) },
                )
            }
            SessionPhase.STARTING, SessionPhase.STOPPING -> primary.addView(
                style.primaryAction("…", busy = true) {},
            )
        }
        host.content.addView(primary)

        val manage = LinearLayout(host.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, style.dp(4), 0, 0)
        }
        manage.addView(style.textAction("Edit") { host.profiles.openEditor(document.id) })
        manage.addView(style.textAction("Logs") { host.profiles.openLogs(document.id) })
        manage.addView(
            style.textAction("Delete", style.errorText) {
                GuiDialogs.confirmDelete(host, document)
            },
        )
        host.content.addView(manage)
    }
}
