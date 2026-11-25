package app.trierarch.ui.gui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.trierarch.feature.profiles.ProfileScreen
import app.trierarch.feature.profiles.ProfilesController
import app.trierarch.feature.profiles.ProfilesEvent
import app.trierarch.feature.profiles.ProfilesState
import app.trierarch.feature.updates.UpdateEvent
import app.trierarch.feature.updates.UpdatesState
import app.trierarch.feature.updates.UpdatesViewModel
import app.trierarch.ui.gui.pages.LogsPage
import app.trierarch.ui.gui.pages.ProfileDetailsPage
import app.trierarch.ui.gui.pages.ProfileEditorPage
import app.trierarch.ui.gui.pages.ProfilesListPage
import app.trierarch.ui.gui.pages.UpdatesPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The GUI host: owns the root layout, collects the two state flows and picks
 * the page. The pages render state and forward intents; none of them talk to
 * Core or the platform.
 */
internal class GuiScreen(
    override val context: Context,
    private val profilesController: ProfilesController,
    private val updatesViewModel: UpdatesViewModel,
    private val onClose: () -> Unit,
    private val onFollowSession: () -> Unit,
    private val onPickArchive: (String) -> Unit,
) : GuiHost {
    override val style = GuiStyle(context)
    override val profiles: ProfilesController get() = profilesController
    override val updates: UpdatesViewModel get() = updatesViewModel
    override var profilesState: ProfilesState = profilesController.state.value
    override var updatesState: UpdatesState = updatesViewModel.state.value

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
        setPadding(style.dp(20), style.dp(16), style.dp(20), style.dp(16))
    }
    override val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val errorLabel = TextView(context).apply {
        setTextColor(style.errorText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, style.dp(8), 0, 0)
        visibility = View.GONE
    }
    override val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private var showUpdates = false
    private var lastProfileScreen: ProfileScreen? = null
    private var imeInset = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        scope.launch {
            profilesController.state.collect { state ->
                profilesState = state
                val screenChanged = state.screen != lastProfileScreen
                lastProfileScreen = state.screen
                if (state.error != null) showError(state.error) else hideError()
                if (!showUpdates && (screenChanged || state.screen !is ProfileScreen.Editor)) {
                    render()
                }
            }
        }
        scope.launch {
            profilesController.events.collect { event ->
                when (event) {
                    ProfilesEvent.FollowSession -> onFollowSession()
                    is ProfilesEvent.Message ->
                        Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
                }
            }
        }
        scope.launch {
            updatesViewModel.state.collect { state ->
                updatesState = state
                if (showUpdates || profilesState.screen == ProfileScreen.List) render()
            }
        }
        scope.launch {
            updatesViewModel.events.collect { event ->
                when (event) {
                    is UpdateEvent.Message ->
                        Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
                }
            }
        }
        render()
    }

    val view: View get() = root

    fun setImeInset(inset: Int) {
        if (inset == imeInset) return
        imeInset = inset
        column.setPadding(style.dp(20), style.dp(16), style.dp(20), style.dp(16) + inset)
    }

    fun refresh() {
        profilesController.refresh()
    }

    fun handleBack(): Boolean {
        if (showUpdates) {
            closeUpdates()
            return true
        }
        val handled = profilesController.back()
        if (handled) render()
        return handled
    }

    fun release() {
        scope.cancel()
    }

    override fun showError(message: String) {
        errorLabel.setTextColor(style.errorText)
        errorLabel.text = message
        errorLabel.visibility = View.VISIBLE
    }

    override fun hideError() {
        errorLabel.visibility = View.GONE
    }

    override fun openUpdates() {
        hideError()
        showUpdates = true
        render()
        if (updatesState.phase == UpdatesState.Phase.NOT_CHECKED) updatesViewModel.check()
    }

    override fun closeUpdates() {
        showUpdates = false
        render()
    }

    override fun pickArchive(name: String) = onPickArchive(name)

    override fun closeGui() = onClose()

    private fun render() {
        header.removeAllViews()
        content.removeAllViews()
        if (showUpdates) {
            UpdatesPage.render(this)
            return
        }
        when (val current = profilesState.screen) {
            ProfileScreen.List -> ProfilesListPage.render(this)
            is ProfileScreen.Details -> ProfileDetailsPage.render(this, current.id)
            is ProfileScreen.Editor -> ProfileEditorPage.render(this, current.id)
            is ProfileScreen.Logs -> LogsPage.render(this, current.id)
        }
    }
}
