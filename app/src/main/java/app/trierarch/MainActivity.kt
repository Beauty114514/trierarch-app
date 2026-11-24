package app.trierarch

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.trierarch.core.Core
import app.trierarch.core.ProfileService
import app.trierarch.core.SessionService
import app.trierarch.input.KeyboardPanelController
import app.trierarch.input.RootKeyboardCapture
import app.trierarch.runtime.RuntimeControlServer
import app.trierarch.terminal.DefaultTerminalViewModel
import app.trierarch.terminal.TrierarchTerminalViewClient
import app.trierarch.ui.gui.GuiScreen
import app.trierarch.ui.FloatingActionMenuView
import app.trierarch.ui.FloatingMenuAction
import app.trierarch.wayland.WaylandSurfaceView
import app.trierarch.x11.X11HostController
import com.termux.view.TerminalView

/** The first Trierarch profile: a shell using the app's private files directory as its home. */
class MainActivity : AppCompatActivity() {
    private val terminalViewModel: DefaultTerminalViewModel by viewModels()
    private lateinit var core: Core
    private var runtimeControlServer: RuntimeControlServer? = null
    private var terminalView: TerminalView? = null
    private lateinit var terminalContainer: FrameLayout
    private lateinit var keyboardPanel: KeyboardPanelController
    private val x11Host by lazy { X11HostController(this) }
    private var x11Starting = false
    private var waylandSurface: WaylandSurfaceView? = null
    private lateinit var floatingMenu: FloatingActionMenuView
    /** Opaque layer that covers the runtime surfaces; the terminal stays alive underneath. */
    private lateinit var guiLayer: FrameLayout
    private lateinit var guiScreen: GuiScreen
    private val rootKeyboard by lazy { RootKeyboardCapture(this) }
    /** Authoritative presentation state for controls scoped to a runtime surface. */
    private var displayedSurface = DisplaySurface.INTERNAL_SHELL
    private val uiPreferences by lazy {
        getSharedPreferences("trierarch-ui", MODE_PRIVATE)
    }
    private var desktopImeMode = DesktopImeMode.OVERLAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desktopImeMode = DesktopImeMode.from(
            uiPreferences.getString(KEY_DESKTOP_IME_MODE, null),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
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
        val profiles = ProfileService(this)
        core = Core(
            profiles = profiles,
            sessionService = SessionService(
                profiles = profiles,
                terminal = terminalViewModel,
                displayHost = object : SessionService.DisplayHost {
                    override fun attachTerminal() = attachTerminalSession()
                    override fun showInternalTerminal() {
                        attachTerminalSession()
                        showTerminal(DisplaySurface.INTERNAL_SHELL)
                    }
                    override fun showRuntimeTerminal() = showTerminal(DisplaySurface.RUNTIME_SHELL)
                    override fun showWayland() = showWaylandSurface()
                    override fun showX11(onReady: () -> Unit, onFailure: (String) -> Unit) =
                        showX11Display(onReady, onFailure)
                    override fun showGui() = openGui()
                    override fun showSession(id: String, display: String) = openSession(id, display)
                    override fun runOnMain(action: () -> Unit) = runOnUiThread(action)
                },
            ),
        )
        runtimeControlServer = RuntimeControlServer(this, core)
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
        guiScreen = GuiScreen(
            context = this,
            core = core,
            onClose = { closeGui() },
            onFollowSession = { hideGuiLayer() },
        )
        guiLayer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(android.graphics.Color.BLACK)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            addView(
                guiScreen.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        terminalContainer.addView(
            guiLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setDisplayedSurface(DisplaySurface.INTERNAL_SHELL)
        ViewCompat.setOnApplyWindowInsetsListener(terminalContainer) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (displayedSurface == DisplaySurface.WAYLAND ||
                displayedSurface == DisplaySurface.X11
            ) {
                applyDesktopBottomInset(
                    if (desktopImeMode == DesktopImeMode.RESIZE) ime else 0,
                )
            }
            floatingMenu.setImeBottomInset(ime)
            guiScreen.setImeInset(ime)
            insets
        }
        setContentView(terminalContainer)
        onBackPressedDispatcher.addCallback(this) {
            if (guiLayer.visibility == View.VISIBLE) {
                if (!guiScreen.handleBack()) closeGui()
            } else {
                finish()
            }
        }
        applyImeMode(displayedSurface)
        ViewCompat.requestApplyInsets(terminalContainer)

        attachTerminalSession()

        // Restore the management frontend the app was last left in. Only the
        // GUI and the CLI are remembered; runtime displays never overwrite it.
        if (Frontend.from(uiPreferences.getString(KEY_FRONTEND, null)) == Frontend.GUI) {
            openGui()
        }
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
        // Switching between two display hosts must not leave the other surface
        // visible: it sits above this one in the view order.
        x11Starting = false
        x11Host.hide()
        if (waylandSurface == null) {
            val surface = WaylandSurfaceView(this)
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
                    hideWaylandSurface()
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
        waylandSurface?.let {
            it.releasePressedKeys()
            it.visibility = android.view.View.INVISIBLE
        }
    }

    /** Re-shows a running session without starting, stopping or restarting it. */
    private fun openSession(id: String, display: String) {
        if (!terminalViewModel.showRuntime(id)) return
        when (display) {
            ProfileService.DISPLAY_X11 -> showX11Display(
                onReady = { attachTerminalSession() },
                onFailure = { message ->
                    Toast.makeText(this, "Unable to show X11: $message", Toast.LENGTH_LONG).show()
                    terminalViewModel.showInternalShell()
                    showTerminal(DisplaySurface.INTERNAL_SHELL)
                    attachTerminalSession()
                },
            )
            ProfileService.DISPLAY_WAYLAND -> {
                showWaylandSurface()
                attachTerminalSession()
            }
            else -> {
                showTerminal(DisplaySurface.RUNTIME_SHELL)
                attachTerminalSession()
            }
        }
    }

    private fun setDisplayedSurface(surface: DisplaySurface) {
        displayedSurface = surface
        applySystemBars(surface)
        applyImeMode(surface)
        floatingMenu.setActions(actionsFor(surface))
        // A runtime surface can appear while the GUI layer is open (for example
        // a CLI-driven run); keyboard focus must stay on the GUI.
        if (guiLayer.visibility == View.VISIBLE) guiLayer.requestFocus()
    }

    /**
     * Covers the runtime surface with the in-app GUI. The terminal view, its
     * session, the shell scrollback and every running runtime stay untouched;
     * only visibility and focus change.
     */
    private fun openGui() {
        if (guiLayer.visibility == View.VISIBLE) return
        rememberFrontend(Frontend.GUI)
        keyboardPanel.hide()
        guiScreen.refresh()
        floatingMenu.visibility = View.GONE
        guiLayer.visibility = View.VISIBLE
        guiLayer.requestFocus()
    }

    /** Returns to the exact session that was displayed before the GUI opened. */
    private fun closeGui() {
        if (guiLayer.visibility != View.VISIBLE) return
        rememberFrontend(Frontend.CLI)
        hideGuiLayer()
        when (displayedSurface) {
            DisplaySurface.INTERNAL_SHELL,
            DisplaySurface.RUNTIME_SHELL,
            -> terminalView?.requestFocus()

            DisplaySurface.X11 -> x11Host.requestFocus()
            DisplaySurface.WAYLAND -> waylandSurface?.requestFocus()
        }
    }

    /**
     * Removes the layer without choosing a focus target. Whoever takes over the
     * display (the session being started, or the caller) owns keyboard focus.
     */
    private fun hideGuiLayer() {
        guiLayer.visibility = View.GONE
        floatingMenu.setActions(actionsFor(displayedSurface))
    }

    /**
     * Persists the management frontend the user is looking at. Runtime displays
     * deliberately never write this: the restored value only answers
     * "GUI or CLI?".
     */
    private fun rememberFrontend(frontend: Frontend) {
        uiPreferences.edit().putString(KEY_FRONTEND, frontend.value).apply()
    }

    /**
     * Edge-to-edge desktop windows no longer resize for the IME automatically,
     * so shrink the desktop surface explicitly when the resize mode is active.
     */
    private fun applyDesktopBottomInset(inset: Int) {
        when (displayedSurface) {
            DisplaySurface.WAYLAND -> waylandSurface?.let { view ->
                val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
                if (params.bottomMargin == inset) return
                params.bottomMargin = inset
                view.layoutParams = params
            }
            DisplaySurface.X11 -> x11Host.setBottomInset(inset)
            else -> Unit
        }
    }

    /** The terminal shows the native status bar; desktop surfaces hide it. */
    private fun applySystemBars(surface: DisplaySurface) {
        val terminal = surface == DisplaySurface.INTERNAL_SHELL ||
            surface == DisplaySurface.RUNTIME_SHELL
        WindowCompat.setDecorFitsSystemWindows(window, terminal)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (terminal) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        controller.isAppearanceLightStatusBars = false
        window.decorView.post { ViewCompat.requestApplyInsets(window.decorView) }
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
        when (displayedSurface) {
            DisplaySurface.X11 -> keyboardPanel.toggle(x11Host::showAndroidKeyboard)
            DisplaySurface.WAYLAND -> waylandSurface?.let { surface ->
                keyboardPanel.toggle(surface::showAndroidKeyboard)
            }
            else -> Unit
        }
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
        // The CLI may have created or deleted a profile while the GUI was hidden.
        if (guiLayer.visibility == View.VISIBLE) guiScreen.refresh()
    }

    override fun onDestroy() {
        rootKeyboard.stop()
        if (::guiScreen.isInitialized) guiScreen.release()
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

    /**
     * The app has two management frontends. Runtime displays (X11, Wayland, a
     * container shell) are profile output, not a frontend, and never change
     * this choice.
     */
    private enum class Frontend(val value: String) {
        GUI("gui"),
        CLI("cli");

        companion object {
            fun from(value: String?): Frontend =
                values().firstOrNull { it.value == value } ?: CLI
        }
    }

    private companion object {
        const val TAG = "TrierarchSurface"
        const val KEY_DESKTOP_IME_MODE = "desktop_ime_mode"
        const val KEY_FRONTEND = "frontend"
        val SECONDARY_TEXT = 0xFF9CA3AF.toInt()
        val ERROR_TEXT = 0xFFF87171.toInt()
        val DIVIDER_COLOR = 0xFF23262B.toInt()
        val RUNNING_TEXT = 0xFF7CE38B.toInt()
        val PENDING_TEXT = 0xFFFBBF24.toInt()
    }

    private enum class DisplaySurface {
        INTERNAL_SHELL,
        RUNTIME_SHELL,
        X11,
        WAYLAND,
    }

}
