package app.trierarch

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.trierarch.runtime.RuntimeControlServer
import app.trierarch.runtime.RuntimeController
import app.trierarch.input.KeyboardPanelController
import app.trierarch.input.RootKeyboardCapture
import app.trierarch.terminal.DefaultTerminalViewModel
import app.trierarch.terminal.TrierarchTerminalViewClient
import app.trierarch.ui.FloatingMenuAction
import app.trierarch.ui.FloatingActionMenuView
import app.trierarch.wayland.TextInputFocus
import app.trierarch.wayland.WaylandSurfaceView
import app.trierarch.x11.X11HostController
import com.termux.view.TerminalView

/** The first Trierarch profile: a shell using the app's private files directory as its home. */
class MainActivity : AppCompatActivity() {
    private val terminalViewModel: DefaultTerminalViewModel by viewModels()
    private lateinit var runtimeController: RuntimeController
    private var runtimeControlServer: RuntimeControlServer? = null
    private var terminalView: TerminalView? = null
    private lateinit var terminalContainer: FrameLayout
    private lateinit var keyboardPanel: KeyboardPanelController
    private val x11Host by lazy { X11HostController(this) }
    private var x11Starting = false
    private var waylandSurface: WaylandSurfaceView? = null
    private lateinit var floatingMenu: FloatingActionMenuView
    private val rootKeyboard by lazy { RootKeyboardCapture(this) }
    /** Authoritative presentation state for controls scoped to a runtime surface. */
    private var displayedSurface = DisplaySurface.INTERNAL_SHELL
    private val uiPreferences by lazy {
        getSharedPreferences("trierarch-ui", MODE_PRIVATE)
    }
    private var desktopImeMode = DesktopImeMode.OVERLAY
    /** True while the Android IME was opened because the guest focused a text input. */
    private var keyboardAutoShown = false
    private val autoHideKeyboard = Runnable {
        if (keyboardAutoShown) {
            keyboardPanel.hide()
            keyboardAutoShown = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desktopImeMode = DesktopImeMode.from(
            uiPreferences.getString(KEY_DESKTOP_IME_MODE, null),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        val terminalView = TerminalView(this, null)
        this.terminalView = terminalView
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        terminalView.keepScreenOn = true
        terminalView.setBackgroundColor(android.graphics.Color.BLACK)
        val initialTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            resources.displayMetrics,
        ).toInt().coerceAtLeast(1)
        terminalView.setTextSize(initialTextSizePx)
        terminalView.setTerminalViewClient(
            TrierarchTerminalViewClient(terminalView, initialTextSizePx),
        )
        terminalContainer = FrameLayout(this).apply {
            addView(
                terminalView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        keyboardPanel = KeyboardPanelController(terminalContainer)
        runtimeController = RuntimeController(
            context = this,
            terminal = terminalViewModel,
            displayHost = object : RuntimeController.DisplayHost {
                override fun attachTerminal() = attachTerminalSession()
                override fun showInternalTerminal() = showTerminal(DisplaySurface.INTERNAL_SHELL)
                override fun showRuntimeTerminal() = showTerminal(DisplaySurface.RUNTIME_SHELL)
                override fun showWayland() = showWaylandSurface()
                override fun showX11(onReady: () -> Unit, onFailure: (String) -> Unit) =
                    showX11Display(onReady, onFailure)
                override fun runOnMain(action: () -> Unit) = runOnUiThread(action)
            },
        )
        runtimeControlServer = RuntimeControlServer(this) { command, completion ->
            runOnUiThread { runtimeController.dispatch(command, completion) }
        }
        floatingMenu = FloatingActionMenuView(
            context = this,
            preferences = getSharedPreferences("trierarch-ui", MODE_PRIVATE),
        )
        terminalContainer.addView(
            floatingMenu,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setDisplayedSurface(DisplaySurface.INTERNAL_SHELL)
        ViewCompat.setOnApplyWindowInsetsListener(terminalContainer) { _, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            floatingMenu.setImeBottomInset(imeHeight)
            // A keyboard the user dismissed manually must not be treated as auto-shown.
            if (imeHeight == 0) keyboardAutoShown = false
            insets
        }
        setContentView(terminalContainer)
        applyImeMode(displayedSurface)
        ViewCompat.requestApplyInsets(terminalContainer)

        attachTerminalSession()
    }

    private fun attachTerminalSession() {
        val view = terminalView ?: return
        val session = terminalViewModel.session
        view.attachSession(session)
        session.setScreenChangedListener(view::onScreenUpdated)
        view.post(view::updateSize)
    }

    private fun showWaylandSurface() {
        Log.i(TAG, "showWaylandSurface current=$displayedSurface wayland=${waylandSurface != null}")
        if (waylandSurface == null) {
            val surface = WaylandSurfaceView(this)
            surface.onTextInputFocusChanged = ::onTextInputFocusChanged
            waylandSurface = surface
            terminalContainer.addView(
                surface,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        waylandSurface?.apply {
            visibility = android.view.View.VISIBLE
            attachHostSurface()
            requestFocus()
        }
        terminalView?.visibility = android.view.View.INVISIBLE
        setDisplayedSurface(DisplaySurface.WAYLAND)
        waylandSurface?.refreshTextInputFocus()
        rootKeyboard.start()
    }

    private fun showX11Display(onReady: () -> Unit, onFailure: (String) -> Unit) {
        // Keep the terminal laid out while the runtime session is created. Native
        // sessions are opened lazily from TerminalView.updateSize(); hiding it
        // before that callback can leave the container with no PTY at all.
        x11Starting = true
        x11Host.showIn(
            terminalContainer,
            onReady = {
                onReady()
                if (x11Starting) {
                    terminalView?.visibility = android.view.View.INVISIBLE
                    setDisplayedSurface(DisplaySurface.X11)
                }
            },
            onFailure = onFailure,
        )
    }

    private fun showTerminal(surface: DisplaySurface) {
        Log.i(TAG, "showTerminal target=$surface current=$displayedSurface")
        x11Starting = false
        rootKeyboard.stop()
        x11Host.hide()
        hideWaylandSurface()
        terminalView?.apply {
            visibility = android.view.View.VISIBLE
            requestFocus()
        }
        setDisplayedSurface(surface)
    }

    private fun hideWaylandSurface() {
        keyboardAutoShown = false
        waylandSurface?.let {
            it.removeCallbacks(autoHideKeyboard)
            it.releasePressedKeys()
            it.visibility = android.view.View.INVISIBLE
        }
    }

    private fun setDisplayedSurface(surface: DisplaySurface) {
        displayedSurface = surface
        applyImeMode(surface)
        floatingMenu.setActions(actionsFor(surface))
    }

    private fun actionsFor(surface: DisplaySurface): List<FloatingMenuAction> = when (surface) {
        DisplaySurface.INTERNAL_SHELL -> emptyList()
        DisplaySurface.RUNTIME_SHELL -> listOf(returnToShellAction())
        DisplaySurface.X11,
        DisplaySurface.WAYLAND
        -> listOf(
            returnToShellAction(),
            keyboardAction(),
            desktopImeModeAction(),
        )
    }

    private fun returnToShellAction() = FloatingMenuAction(
        id = "return-shell",
        icon = R.drawable.ic_floating_action_terminal,
        contentDescription = "Return to Trierarch shell",
    ) {
        terminalViewModel.showInternalShell()
        showTerminal(DisplaySurface.INTERNAL_SHELL)
        attachTerminalSession()
    }

    private fun keyboardAction() = FloatingMenuAction(
        id = "toggle-keyboard",
        icon = R.drawable.ic_floating_action_keyboard,
        contentDescription = "Show or hide Android keyboard",
    ) {
        keyboardAutoShown = false
        when (displayedSurface) {
            DisplaySurface.X11 -> keyboardPanel.toggle(x11Host::showAndroidKeyboard)
            DisplaySurface.WAYLAND -> waylandSurface?.let { surface ->
                keyboardPanel.toggle(surface::showAndroidKeyboard)
            }
            else -> Unit
        }
    }

    /**
     * Opens the Android keyboard when the guest focuses a text input, but only
     * for a tap the user just made and only without a physical keyboard. A
     * keyboard opened this way closes again when the guest focus leaves.
     */
    private fun onTextInputFocusChanged(focus: TextInputFocus) {
        if (displayedSurface != DisplaySurface.WAYLAND) return
        val surface = waylandSurface ?: return
        if (!focus.active) {
            // Guest focus can move between two text fields; a short delay avoids
            // hiding and immediately reopening the keyboard for such a move.
            if (keyboardAutoShown) surface.postDelayed(autoHideKeyboard, AUTO_HIDE_DELAY_MILLIS)
            return
        }
        surface.removeCallbacks(autoHideKeyboard)
        if (keyboardAutoShown || keyboardPanel.isShowing()) return
        val hardwareKeyboard =
            resources.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        if (hardwareKeyboard || !surface.hasWindowFocus()) return
        if (!surface.didUserTapRecently(TEXT_FIELD_TAP_MILLIS)) return
        surface.showAndroidKeyboard()
        keyboardAutoShown = true
    }

    private fun desktopImeModeAction() = FloatingMenuAction(
        id = "desktop-ime-mode",
        icon = if (desktopImeMode == DesktopImeMode.OVERLAY) {
            R.drawable.ic_floating_action_ime_overlay
        } else {
            R.drawable.ic_floating_action_ime_resize
        },
        contentDescription = if (desktopImeMode == DesktopImeMode.OVERLAY) {
            "Keyboard overlays the desktop; switch to resizing it"
        } else {
            "Keyboard resizes the desktop; switch to overlaying it"
        },
    ) {
        toggleDesktopImeMode()
    }

    private fun toggleDesktopImeMode() {
        desktopImeMode = when (desktopImeMode) {
            DesktopImeMode.OVERLAY -> DesktopImeMode.RESIZE
            DesktopImeMode.RESIZE -> DesktopImeMode.OVERLAY
        }
        uiPreferences.edit()
            .putString(KEY_DESKTOP_IME_MODE, desktopImeMode.value)
            .apply()
        setDisplayedSurface(displayedSurface)
        Toast.makeText(
            this,
            if (desktopImeMode == DesktopImeMode.OVERLAY) {
                "Keyboard now overlays the desktop"
            } else {
                "Keyboard now resizes the desktop"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun applyImeMode(surface: DisplaySurface) {
        val mode = when (surface) {
            DisplaySurface.X11,
            DisplaySurface.WAYLAND -> desktopImeMode
            else -> DesktopImeMode.RESIZE
        }
        window.setSoftInputMode(
            when (mode) {
                DesktopImeMode.OVERLAY -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                DesktopImeMode.RESIZE -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            },
        )
        window.decorView.post {
            ViewCompat.requestApplyInsets(window.decorView)
            window.decorView.requestLayout()
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause display=$displayedSurface")
        rootKeyboard.stop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume display=$displayedSurface")
        if (displayedSurface == DisplaySurface.WAYLAND) rootKeyboard.start()
    }

    override fun onDestroy() {
        rootKeyboard.stop()
        runtimeControlServer?.close()
        runtimeControlServer = null
        super.onDestroy()
    }

    private enum class DesktopImeMode(val value: String) {
        OVERLAY("overlay"),
        RESIZE("resize");

        companion object {
            fun from(value: String?): DesktopImeMode =
                values().firstOrNull { it.value == value } ?: OVERLAY
        }
    }

    private companion object {
        const val TAG = "TrierarchSurface"
        const val KEY_DESKTOP_IME_MODE = "desktop_ime_mode"
        const val TEXT_FIELD_TAP_MILLIS = 1000L
        const val AUTO_HIDE_DELAY_MILLIS = 300L
    }

    private enum class DisplaySurface {
        INTERNAL_SHELL,
        RUNTIME_SHELL,
        X11,
        WAYLAND,
    }

}
