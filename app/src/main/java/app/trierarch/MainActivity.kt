package app.trierarch

import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.trierarch.runtime.RuntimeControlServer
import app.trierarch.runtime.RuntimeController
import app.trierarch.terminal.DefaultTerminalViewModel
import app.trierarch.terminal.TrierarchTerminalViewClient
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
    private val x11Host by lazy { X11HostController(this) }
    private var x11Starting = false
    private var waylandSurface: WaylandSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        runtimeController = RuntimeController(
            context = this,
            terminal = terminalViewModel,
            displayHost = object : RuntimeController.DisplayHost {
                override fun attachTerminal() = attachTerminalSession()
                override fun showTerminal() = this@MainActivity.showTerminal()
                override fun showWayland() = showWaylandSurface()
                override fun showX11(onReady: () -> Unit, onFailure: (String) -> Unit) =
                    showX11Display(onReady, onFailure)
                override fun runOnMain(action: () -> Unit) = runOnUiThread(action)
            },
        )
        runtimeControlServer = RuntimeControlServer(this) { command, completion ->
            runOnUiThread { runtimeController.dispatch(command, completion) }
        }
        setContentView(terminalContainer)

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
        if (waylandSurface == null) {
            waylandSurface = WaylandSurfaceView(this)
            terminalContainer.addView(
                waylandSurface,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        waylandSurface?.requestFocus()
        terminalView?.visibility = android.view.View.INVISIBLE
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
                if (x11Starting) terminalView?.visibility = android.view.View.INVISIBLE
            },
            onFailure = onFailure,
        )
    }

    private fun showTerminal() {
        x11Starting = false
        x11Host.hide()
        hideWaylandSurface()
        terminalView?.apply {
            visibility = android.view.View.VISIBLE
            requestFocus()
        }
    }

    private fun hideWaylandSurface() {
        waylandSurface?.let {
            it.releasePressedKeys()
            terminalContainer.removeView(it)
        }
        waylandSurface = null
    }

    override fun onDestroy() {
        runtimeControlServer?.close()
        runtimeControlServer = null
        super.onDestroy()
    }

}
