package app.trierarch.wayland

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.MotionEvent
import android.view.KeyEvent
import android.os.SystemClock
import android.graphics.PixelFormat
import android.util.Log
import app.trierarch.input.PointerInputRouter
import app.trierarch.input.PhysicalKeyEvent
import app.trierarch.input.PhysicalKeyboardRouter
import app.trierarch.input.AndroidImeController

/** Full-screen, display-only target for the first Wayland milestone. */
class WaylandSurfaceView(
    context: Context,
) : SurfaceView(context), SurfaceHolder.Callback {
    private val inputRouter = PointerInputRouter(context, WaylandPointerEventSink) {
        WaylandBridge.setCursorVisible(it)
    }
    private val keyboardRouter = PhysicalKeyboardRouter { event ->
        WaylandBridge.setKeyboardKey(
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            pressed = event.action == PhysicalKeyEvent.Action.DOWN,
            timeMillis = event.eventTime.toWaylandTime(),
        )
    }
    private val waylandImeSink = WaylandImeEventSink(
        socket = WaylandImeBridgeRuntime.socket(context),
    )
    private val androidIme = AndroidImeController(this, waylandImeSink)

    /** Guest text-input focus as last reported by the IME bridge. */
    var onTextInputFocusChanged: ((TextInputFocus) -> Unit)? = null
    private val textInputStateFile = WaylandImeBridgeRuntime.state(context)
    private var lastFocusSequence = Int.MIN_VALUE
    private var lastPointerPressMillis = 0L
    private val focusPoll = object : Runnable {
        override fun run() {
            pollTextInputFocus()
            postDelayed(this, FOCUS_POLL_MILLIS)
        }
    }

    init {
        // A state file left by an earlier session must not pop the keyboard on
        // its own; the bridge replaces it once the guest focuses a text input.
        WaylandTextInputState.reset(textInputStateFile)
        holder.setFormat(PixelFormat.RGBA_8888)
        holder.addCallback(this)
        // The pixels are rendered by the separate Surface. Keeping the
        // ordinary View layer transparent prevents it from covering that
        // Surface with an opaque background, matching the X11 host view.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceCreated surface=${holder.surface.hashCode()} valid=${holder.surface.isValid}")
        attachHostSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "surfaceChanged surface=${holder.surface.hashCode()} ${width}x$height format=$format valid=${holder.surface.isValid}")
        updateOutputSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceDestroyed surface=${holder.surface.hashCode()} valid=${holder.surface.isValid}")
        WaylandBridge.detachSurface(holder.surface)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            lastPointerPressMillis = SystemClock.uptimeMillis()
        }
        return inputRouter.onTouchEvent(this, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
            lastPointerPressMillis = SystemClock.uptimeMillis()
        }
        return inputRouter.onGenericMotionEvent(this, event) || super.onGenericMotionEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean =
        inputRouter.onHoverEvent(this, event) || super.onHoverEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        keyboardRouter.dispatchAndroidEvent(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keyboardRouter.dispatchAndroidEvent(event) || super.onKeyUp(keyCode, event)

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection =
        androidIme.createInputConnection(outAttrs)

    fun releasePressedKeys() {
        keyboardRouter.releaseAll(SystemClock.uptimeMillis())
        waylandImeSink.resetInputState()
    }

    fun showAndroidKeyboard() = androidIme.showKeyboard()

    /** Re-evaluates text-input focus now instead of waiting for the next poll. */
    fun refreshTextInputFocus() {
        lastFocusSequence = Int.MIN_VALUE
        pollTextInputFocus()
    }

    /** Whether the user touched the desktop recently enough to open the IME. */
    fun didUserTapRecently(windowMillis: Long): Boolean =
        SystemClock.uptimeMillis() - lastPointerPressMillis <= windowMillis

    private fun pollTextInputFocus() {
        if (!isShown) {
            // Re-report the current state when the surface becomes visible again.
            lastFocusSequence = Int.MIN_VALUE
            return
        }
        val focus = WaylandTextInputState.read(textInputStateFile)
        if (focus.sequence == lastFocusSequence) return
        lastFocusSequence = focus.sequence
        onTextInputFocusChanged?.invoke(focus)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(focusPoll)
    }

    /** Reattaches a preserved Android Surface after the Wayland host restarts. */
    fun attachHostSurface() {
        val surface = holder.surface
        if (!surface.isValid) return
        Log.i(TAG, "attachHostSurface surface=${surface.hashCode()} frame=${holder.surfaceFrame.width()}x${holder.surfaceFrame.height()}")
        VulkanRuntime.ensureConfigured(context)
        WaylandBridge.attachSurface(surface)
        updateOutputSize(holder.surfaceFrame.width(), holder.surfaceFrame.height())
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            inputRouter.cancel(this)
            releasePressedKeys()
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) releasePressedKeys()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(focusPoll)
        inputRouter.cancel(this)
        releasePressedKeys()
        waylandImeSink.close()
        super.onDetachedFromWindow()
    }

    private fun updateOutputSize(width: Int, height: Int) {
        WaylandBridge.setOutputSize(width, height)
    }

    private fun Long.toWaylandTime(): Int = (this and 0x7fff_ffffL).toInt()

    private companion object {
        const val TAG = "TrierarchSurface"
        const val FOCUS_POLL_MILLIS = 250L
    }
}
