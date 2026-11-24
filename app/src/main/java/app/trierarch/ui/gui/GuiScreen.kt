package app.trierarch.ui.gui

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.trierarch.core.Command
import app.trierarch.core.CommandOutcome
import app.trierarch.core.Core
import app.trierarch.core.CoreEvent
import app.trierarch.core.DocumentContent
import app.trierarch.core.ProfileDocument
import app.trierarch.core.ProfileService
import app.trierarch.core.SessionPhase
import app.trierarch.core.SessionSnapshot
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The in-app GUI: a view over [Core]. It never owns sessions or writes profile
 * files; every action goes through the core and every refresh re-reads it.
 */
class GuiScreen(
    private val context: Context,
    private val core: Core,
    private val onClose: () -> Unit,
    private val onFollowSession: () -> Unit,
) {
    private val root = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        // Opaque and clickable: neither touches nor keys may reach the runtime
        // surface underneath.
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    }
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
    }
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val errorLabel = TextView(context).apply {
        setTextColor(errorText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(8), 0, 0)
        visibility = View.GONE
    }
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private var screen: Screen = Screen.List
    private var editor: EditText? = null
    private var imeInset = 0

    private val listener: (CoreEvent) -> Unit = { event ->
        if (event == CoreEvent.DocumentsChanged && screen !is Screen.Editor) render()
    }

    init {
        column.addView(header)
        column.addView(errorLabel)
        column.addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    content,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        core.addListener(listener)
        render()
    }

    val view: View get() = root

    /** Keeps the editor above the keyboard on every display surface. */
    fun setImeInset(inset: Int) {
        if (inset == imeInset) return
        imeInset = inset
        column.setPadding(dp(20), dp(16), dp(20), dp(16) + inset)
    }

    /** Re-reads the core; never touches an editor with unsaved text. */
    fun refresh() {
        if (screen is Screen.Editor) return
        render()
    }

    /** Returns true when it navigated inside the GUI; false at the root list. */
    fun handleBack(): Boolean = when (val current = screen) {
        Screen.List -> false
        is Screen.Editor -> {
            editor = null
            hideError()
            screen = Screen.Details(current.id)
            render()
            true
        }
        is Screen.Details -> {
            hideError()
            screen = Screen.List
            render()
            true
        }
    }

    fun release() {
        core.removeListener(listener)
    }

    private fun render() {
        header.removeAllViews()
        content.removeAllViews()
        when (val current = screen) {
            Screen.List -> renderList()
            is Screen.Details -> renderDetails(current.id)
            is Screen.Editor -> Unit
        }
    }

    private fun renderList() {
        header.addView(
            title("Trierarch"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(headerButton("Create") { promptCreate() })
        header.addView(headerButton("Terminal") { onClose() })

        val documents = core.documents()
        val snapshot = core.sessions()
        content.addView(sectionLabel("ENVIRONMENTS · ${documents.size}"))
        if (documents.isEmpty()) {
            content.addView(emptyState())
            return
        }
        val busy = snapshot.pending != null
        documents.forEach { document ->
            content.addView(environmentRow(document, snapshot.sessions[document.id], busy))
        }
    }

    private fun renderDetails(id: String) {
        val document = core.documents().firstOrNull { it.id == id }
        if (document == null) {
            screen = Screen.List
            render()
            return
        }
        header.addView(
            title(document.id),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(headerButton("Back") {
            hideError()
            screen = Screen.List
            render()
        })

        content.addView(sectionLabel("PROFILE"))
        content.addView(info("runtime", document.runtime.ifEmpty { "—" }))
        content.addView(
            info(
                "display",
                if (document.display == ProfileService.DISPLAY_NONE) "terminal" else document.display,
            ),
        )
        content.addView(info("renderer", document.renderer ?: "—"))
        val snapshot = core.sessions()
        val session = snapshot.sessions[document.id]
        content.addView(info("state", phaseText(session)))

        document.error?.let { message ->
            content.addView(TextView(context).apply {
                text = message
                setTextColor(errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(12), 0, 0)
            })
        }

        val primary = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, 0)
        }
        val busy = snapshot.pending != null
        when (session?.phase ?: SessionPhase.STOPPED) {
            SessionPhase.STOPPED -> primary.addView(
                primaryAction("Start", busy) { submit(Command.Session.Run(document.id)) },
            )
            SessionPhase.RUNNING -> {
                primary.addView(
                    primaryAction("Open", busy) { submit(Command.Session.Open(document.id)) },
                )
                primary.addView(
                    secondaryAction("Stop", busy) { submit(Command.Session.Stop(document.id)) },
                )
            }
            SessionPhase.STARTING, SessionPhase.STOPPING -> primary.addView(
                primaryAction("…", busy = true) {},
            )
        }
        content.addView(primary)

        val manage = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        manage.addView(textAction("Edit") { openEditor(document.id) })
        manage.addView(textAction("Delete", errorText) { confirmDelete(document) })
        content.addView(manage)
    }

    private fun renderEditor(document: DocumentContent) {
        header.removeAllViews()
        content.removeAllViews()
        header.addView(
            title("Edit ${document.id}"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(headerButton("Save") { saveEditor() })
        header.addView(headerButton("Cancel") { cancelEditor() })

        val field = EditText(context).apply {
            setText(document.text)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setBackgroundColor(fieldColor)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.START
            minLines = 18
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        editor = field
        content.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        content.addView(TextView(context).apply {
            text = "Raw TOML. Save checks that the file has not changed since it was opened."
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun environmentRow(
        document: ProfileDocument,
        session: SessionSnapshot?,
        busy: Boolean,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { openDetails(document.id) }
        }
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(context).apply {
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
            details.addView(TextView(context).apply {
                text = badges
                setTextColor(secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, 0)
            })
        }
        val phase = session?.phase ?: SessionPhase.STOPPED
        details.addView(TextView(context).apply {
            text = phaseText(session) + if (session?.active == true) " · shown" else ""
            setTextColor(
                when (phase) {
                    SessionPhase.STARTING, SessionPhase.STOPPING -> pendingText
                    SessionPhase.RUNNING -> runningText
                    SessionPhase.STOPPED -> secondaryText
                },
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, 0)
        })
        document.error?.let { message ->
            details.addView(TextView(context).apply {
                text = message
                setTextColor(errorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, 0)
            })
        }
        line.addView(
            details,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        rowActions(document, phase, busy)?.let { actions ->
            line.addView(
                actions,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(12) },
            )
        }
        row.addView(line)
        row.addView(
            View(context).apply { setBackgroundColor(dividerColor) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(14)
            },
        )
        return row
    }

    private fun rowActions(
        document: ProfileDocument,
        phase: SessionPhase,
        busy: Boolean,
    ): View? {
        if (document.error != null) return null
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        when (phase) {
            SessionPhase.STOPPED -> actions.addView(
                primaryAction("Start", busy) { submit(Command.Session.Run(document.id)) },
            )
            SessionPhase.RUNNING -> {
                actions.addView(
                    primaryAction("Open", busy) { submit(Command.Session.Open(document.id)) },
                )
                actions.addView(
                    secondaryAction("Stop", busy) { submit(Command.Session.Stop(document.id)) },
                )
            }
            SessionPhase.STARTING, SessionPhase.STOPPING -> actions.addView(
                primaryAction("…", busy = true) {},
            )
        }
        return actions
    }

    private fun openDetails(id: String) {
        hideError()
        editor = null
        screen = Screen.Details(id)
        render()
    }

    private fun openEditor(id: String) {
        hideError()
        val document = core.read(id)
        if (document == null) {
            screen = Screen.List
            showError("profile does not exist: $id")
            render()
            return
        }
        screen = Screen.Editor(id, document.revision)
        renderEditor(document)
    }

    private fun saveEditor() {
        val field = editor ?: return
        val current = screen as? Screen.Editor ?: return
        submit(
            Command.Document.Save(current.id, field.text.toString(), current.revision),
            afterSuccess = {
                editor = null
                screen = Screen.List
            },
        )
    }

    private fun cancelEditor() {
        val current = screen as? Screen.Editor ?: return
        editor = null
        hideError()
        screen = Screen.Details(current.id)
        render()
    }

    private fun promptCreate() {
        val input = EditText(context).apply {
            hint = "profile id"
            setSingleLine()
            typeface = Typeface.MONOSPACE
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("New environment")
            .setMessage("Creates the profile template; edit it before running.")
            .setView(input)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val id = input.text.toString().trim()
                if (id.isEmpty()) {
                    showError("profile id is empty")
                    return@setOnClickListener
                }
                dialog.dismiss()
                submit(Command.Document.Create(id), afterSuccess = { screen = Screen.List })
            }
        }
        dialog.show()
    }

    private fun confirmDelete(document: ProfileDocument) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Delete ${document.id}?")
            .setMessage("The profile file is removed. A running session is not stopped.")
            .setPositiveButton("Delete") { _, _ ->
                val current = core.read(document.id)
                if (current == null) {
                    showError("profile does not exist: ${document.id}")
                    return@setPositiveButton
                }
                submit(
                    Command.Document.Delete(current.id, current.revision),
                    afterSuccess = { screen = Screen.List },
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submit(command: Command, afterSuccess: (() -> Unit)? = null) {
        hideError()
        core.submit(command) { outcome ->
            when (outcome) {
                is CommandOutcome.Failure -> showError(outcome.error.message)
                is CommandOutcome.Success -> {
                    if (command is Command.Session.Run || command is Command.Session.Open) {
                        // Follow the session and let its display host own focus.
                        onFollowSession()
                    }
                    outcome.afterReply?.invoke()
                    afterSuccess?.invoke()
                    if (command is Command.Session.Stop) render()
                }
            }
        }
    }

    private fun showError(message: String) {
        errorLabel.text = message
        errorLabel.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorLabel.visibility = View.GONE
    }

    private fun phaseText(session: SessionSnapshot?): String = when (session?.phase ?: SessionPhase.STOPPED) {
        SessionPhase.STOPPED -> "stopped"
        SessionPhase.STARTING -> "starting"
        SessionPhase.RUNNING -> "running"
        SessionPhase.STOPPING -> "stopping"
    }

    private fun title(text: String): TextView = TextView(context).apply {
        setText(text)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
    }

    private fun headerButton(label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(context).apply {
            minWidth = 0
            text = label
            setOnClickListener { onClick() }
        }

    private fun sectionLabel(text: String): TextView = TextView(context).apply {
        setText(text)
        setTextColor(secondaryText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.08f
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun info(label: String, value: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, 0)
        addView(TextView(context).apply {
            setText(label)
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            width = dp(88)
        })
        addView(
            TextView(context).apply {
                setText(value)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.MONOSPACE
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun primaryAction(label: String, busy: Boolean, onClick: () -> Unit) =
        MaterialButton(context).apply {
            minWidth = 0
            text = label
            isEnabled = !busy
            setOnClickListener { onClick() }
        }

    private fun secondaryAction(label: String, busy: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(errorText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            isEnabled = !busy
            alpha = if (busy) 0.4f else 1f
            setOnClickListener { onClick() }
        }

    private fun textAction(label: String, color: Int = secondaryText, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun emptyState(): View = TextView(context).apply {
        text = "No environments yet.\n\nUse Create to add one, or from the terminal:\n\n" +
            "  trierarch --create arch-demo\n" +
            "  trierarch --config arch-demo"
        setTextColor(secondaryText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.MONOSPACE
        setPadding(0, dp(16), 0, 0)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    private sealed interface Screen {
        data object List : Screen
        data class Details(val id: String) : Screen
        data class Editor(val id: String, val revision: String) : Screen
    }

    private companion object {
        val secondaryText = 0xFF9CA3AF.toInt()
        val errorText = 0xFFF87171.toInt()
        val dividerColor = 0xFF23262B.toInt()
        val runningText = 0xFF7CE38B.toInt()
        val pendingText = 0xFFFBBF24.toInt()
        val fieldColor = 0xFF111318.toInt()
    }
}
