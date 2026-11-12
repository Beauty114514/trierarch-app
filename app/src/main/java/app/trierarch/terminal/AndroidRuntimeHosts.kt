package app.trierarch.terminal

import android.app.Application
import app.trierarch.audio.PipeWireHostController
import app.trierarch.compat.GbmShimRuntime
import app.trierarch.compat.GuestCompatibilityRuntime
import app.trierarch.config.ProfileStore
import app.trierarch.virgl.VirglHostController
import app.trierarch.wayland.WaylandBridge
import app.trierarch.wayland.WaylandImeBridgeRuntime
import app.trierarch.x11.X11Runtime
import java.io.File

internal class AndroidRuntimeHosts(private val app: Application) : RuntimeHosts {
    override fun startVirgl(venus: Boolean): String =
        VirglHostController.start(app, venus).absolutePath

    override fun startWayland(bufferMode: String, desktopScalePercent: Int) {
        check(WaylandBridge.start(app, bufferMode, desktopScalePercent)) {
            "Unable to start Wayland host"
        }
    }

    override fun startPipeWire(): String = PipeWireHostController.start(app).absolutePath

    override fun stopWayland() = WaylandBridge.stop()

    override fun stopVirgl() = VirglHostController.stop()

    override fun stopPipeWire() = PipeWireHostController.stop()

    override fun installGbmShim(waylandRuntimeDirectory: String) {
        GbmShimRuntime.install(app, File(waylandRuntimeDirectory))
    }
}

internal class AndroidRuntimePaths(private val app: Application) : RuntimePaths {
    override fun x11SocketDirectory(display: String): String? =
        X11Runtime.socketDirectory(app).absolutePath.takeIf {
            display == ProfileStore.DISPLAY_X11
        }

    override fun waylandRuntimeDirectory(display: String): String? =
        File(app.filesDir, "wayland/runtime").also { it.mkdirs() }.absolutePath.takeIf {
            display == ProfileStore.DISPLAY_WAYLAND
        }

    override fun waylandImeBridge(display: String): String? =
        if (display == ProfileStore.DISPLAY_WAYLAND) {
            WaylandImeBridgeRuntime.executable(app).absolutePath
        } else {
            null
        }

    override fun udevCompatibilityLibrary(enabled: Boolean): String =
        GuestCompatibilityRuntime.udevMonitorLibrary(app, enabled)?.absolutePath.orEmpty()
}
