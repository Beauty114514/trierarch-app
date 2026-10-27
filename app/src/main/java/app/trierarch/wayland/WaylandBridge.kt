package app.trierarch.wayland

import android.content.Context
import android.view.Surface
import java.io.File

object WaylandBridge {
    const val AXIS_SOURCE_WHEEL = 0
    const val AXIS_SOURCE_FINGER = 1
    const val BUFFER_SHM = "shm"
    const val BUFFER_DMABUF = "dmabuf"

    init {
        System.loadLibrary("wayland-server")
        System.loadLibrary("ffi")
        System.loadLibrary("trierarch-wayland-host")
    }

    /** Buffer path the running server was started with, or null when stopped. */
    private var activeBufferMode: String? = null

    @JvmStatic
    fun start(context: Context, bufferMode: String): Boolean {
        val runtime = File(context.filesDir, "wayland/runtime")
        check(runtime.isDirectory || runtime.mkdirs()) { "Unable to create Wayland runtime directory" }
        val mode = if (bufferMode == BUFFER_SHM) BUFFER_SHM else BUFFER_DMABUF
        // The server owns the linux-dmabuf global for its whole lifetime and
        // cannot switch buffer paths without disconnecting every client.
        // Refuse a conflicting session instead of silently tearing down the
        // display of one that is already running; closing the last Wayland
        // session stops the host, so switching modes afterwards works.
        if (activeBufferMode != null && activeBufferMode != mode) {
            android.util.Log.w(
                "TrierarchWayland",
                "refusing to start Wayland host: running buffer mode $activeBufferMode, requested $mode",
            )
            return false
        }
        val started = nativeStart(runtime.absolutePath, mode)
        activeBufferMode = if (started) mode else null
        return started
    }

    @JvmStatic
    fun stop() {
        nativeStop()
        activeBufferMode = null
    }

    @JvmStatic
    fun attachSurface(surface: Surface) = nativeAttachSurface(surface)

    @JvmStatic
    fun setOutputSize(width: Int, height: Int) {
        if (width > 0 && height > 0) nativeSetOutputSize(width, height)
    }

    @JvmStatic fun movePointerAbsolute(x: Float, y: Float, timeMillis: Int) =
        nativeMovePointerAbsolute(x, y, timeMillis)

    @JvmStatic fun movePointerRelative(deltaX: Float, deltaY: Float, timeMillis: Int) =
        nativeMovePointerRelative(deltaX, deltaY, timeMillis)

    @JvmStatic fun setPointerButton(button: Int, pressed: Boolean, timeMillis: Int) =
        nativeSetPointerButton(button, pressed, timeMillis)

    @JvmStatic fun scrollPointer(deltaX: Float, deltaY: Float, source: Int, timeMillis: Int) =
        nativeScrollPointer(deltaX, deltaY, source, timeMillis)

    @JvmStatic fun resetPointer(timeMillis: Int) = nativeResetPointer(timeMillis)

    @JvmStatic fun setKeyboardKey(
        keyCode: Int,
        scanCode: Int,
        pressed: Boolean,
        timeMillis: Int,
    ): Boolean = nativeSetKeyboardKey(keyCode, scanCode, pressed, timeMillis)

    @JvmStatic fun setCursorVisible(visible: Boolean) = nativeSetCursorVisible(visible)


    private external fun nativeStart(runtimeDirectory: String, bufferMode: String): Boolean
    private external fun nativeStop()
    private external fun nativeAttachSurface(surface: Surface)
    private external fun nativeSetOutputSize(width: Int, height: Int)
    private external fun nativeMovePointerAbsolute(x: Float, y: Float, timeMillis: Int)
    private external fun nativeMovePointerRelative(deltaX: Float, deltaY: Float, timeMillis: Int)
    private external fun nativeSetPointerButton(button: Int, pressed: Boolean, timeMillis: Int)
    private external fun nativeScrollPointer(deltaX: Float, deltaY: Float, source: Int, timeMillis: Int)
    private external fun nativeResetPointer(timeMillis: Int)
    private external fun nativeSetKeyboardKey(
        keyCode: Int,
        scanCode: Int,
        pressed: Boolean,
        timeMillis: Int,
    ): Boolean
    private external fun nativeSetCursorVisible(visible: Boolean)
}
