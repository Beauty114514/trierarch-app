package app.trierarch.ui.gui.pages

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.trierarch.core.ProfileDocument
import app.trierarch.core.ProfileService
import app.trierarch.core.SessionPhase
import app.trierarch.core.SessionSnapshot
import app.trierarch.ui.gui.GuiDialogs
import app.trierarch.ui.gui.GuiHost
import app.trierarch.ui.gui.hasAvailableUpdate
import app.trierarch.ui.gui.phaseText
import app.trierarch.ui.gui.summaryText

internal object ProfilesListPage {
    fun render(host: GuiHost) {
        val style = host.style
        host.header.addView(
            style.title("Trierarch"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        host.header.addView(style.headerButton("Create") { GuiDialogs.promptCreate(host) })
        host.header.addView(style.headerButton("Import") { GuiDialogs.promptImport(host) })
        host.header.addView(style.headerButton("Terminal") { host.closeGui() })

        val state = host.profilesState
        host.content.addView(updatesEntry(host))
        host.content.addView(style.sectionLabel("ENVIRONMENTS · ${state.documents.size}"))
        if (state.documents.isEmpty()) {
            host.content.addView(emptyState(host))
            return
        }
        val busy = state.sessions.pending != null
        state.documents.forEach { document ->
            host.content.addView(
                environmentRow(host, document, state.sessions.sessions[document.id], busy),
            )
        }
    }

    private fun updatesEntry(host: GuiHost): View {
        val style = host.style
        val row = LinearLayout(host.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, style.dp(16), 0, style.dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { host.openUpdates() }
        }
        val labels = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(host.context).apply {
            setText("Updates")
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        })
        labels.addView(TextView(host.context).apply {
            setText(host.updatesState.summaryText())
            setTextColor(
                if (host.updatesState.hasAvailableUpdate()) style.pendingText else style.secondaryText,
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, style.dp(4), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(host.context).apply {
            setText("›")
            setTextColor(style.secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        })
        return LinearLayout(host.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(
                style.divider(),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(1)),
            )
        }
    }

    private fun environmentRow(
        host: GuiHost,
        document: ProfileDocument,
        session: SessionSnapshot?,
        busy: Boolean,
    ): View {
        val style = host.style
        val row = LinearLayout(host.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, style.dp(14), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { host.profiles.openDetails(document.id) }
        }
        val line = LinearLayout(host.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val details = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(host.context).apply {
            text = document.id
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.MONOSPACE
        })
        if (document.error == null) {
            val display = if (document.display == ProfileService.DISPLAY_NONE) {
                "terminal"
            } else {
                document.display
            }
            val badges = listOf(document.runtime, display, document.renderer)
                .filterNotNull()
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            details.addView(TextView(host.context).apply {
                text = badges
                setTextColor(style.secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, style.dp(4), 0, 0)
            })
        }
        val phase = session?.phase ?: SessionPhase.STOPPED
        details.addView(TextView(host.context).apply {
            text = phaseText(session) + if (session?.active == true) " · shown" else ""
            setTextColor(
                when (phase) {
                    SessionPhase.STARTING, SessionPhase.STOPPING -> style.pendingText
                    SessionPhase.RUNNING -> style.runningText
                    SessionPhase.STOPPED -> style.secondaryText
                },
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, style.dp(4), 0, 0)
        })
        document.error?.let { message ->
            details.addView(TextView(host.context).apply {
                text = message
                setTextColor(style.errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, style.dp(4), 0, 0)
            })
        }
        line.addView(
            details,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        rowActions(host, document, phase, busy)?.let { actions ->
            line.addView(
                actions,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = style.dp(12) },
            )
        }
        row.addView(line)
        row.addView(
            style.divider(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(1)).apply {
                topMargin = style.dp(14)
            },
        )
        return row
    }

    private fun rowActions(
        host: GuiHost,
        document: ProfileDocument,
        phase: SessionPhase,
        busy: Boolean,
    ): View? {
        if (document.error != null) return null
        val style = host.style
        val actions = LinearLayout(host.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        when (phase) {
            SessionPhase.STOPPED -> actions.addView(
                style.primaryAction("Start", busy) { host.profiles.run(document.id) },
            )
            SessionPhase.RUNNING -> {
                actions.addView(
                    style.primaryAction("Open", busy) { host.profiles.open(document.id) },
                )
                actions.addView(
                    style.secondaryAction("Stop", busy) { host.profiles.stop(document.id) },
                )
            }
            SessionPhase.STARTING, SessionPhase.STOPPING -> actions.addView(
                style.primaryAction("…", busy = true) {},
            )
        }
        return actions
    }

    private fun emptyState(host: GuiHost): View {
        val style = host.style
        return TextView(host.context).apply {
            setText(
                "No environments yet.\n\nUse Create to add one, or from the terminal:\n\n" +
                    "  trierarch --create arch-demo\n" +
                    "  trierarch --config arch-demo",
            )
            setTextColor(style.secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding(0, style.dp(16), 0, 0)
        }
    }
}
