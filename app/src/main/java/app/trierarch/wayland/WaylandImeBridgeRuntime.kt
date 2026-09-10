package app.trierarch.wayland

import android.content.Context
import java.io.File

/** Installs the guest-only IME bridge next to the app-owned Wayland runtime. */
object WaylandImeBridgeRuntime {
    private const val assetPath = "wayland-ime/arm64-v8a/trierarch-wayland-ime-bridge"
    private const val fileName = "trierarch-wayland-ime-bridge"

    fun executable(context: Context): File {
        val directory = File(context.filesDir, "wayland/runtime/ime")
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create Wayland IME runtime directory"
        }
        val destination = File(directory, fileName)
        if (!destination.isFile || destination.length() == 0L) {
            val temporary = File(directory, ".${fileName}.${System.nanoTime()}.tmp")
            context.assets.open(assetPath).use { input ->
                temporary.outputStream().use(input::copyTo)
            }
            check(temporary.renameTo(destination)) { "Unable to install Wayland IME bridge" }
        }
        check(destination.setExecutable(true, false)) { "Unable to mark Wayland IME bridge executable" }
        return destination
    }

    fun socket(context: Context): File = File(context.filesDir, "wayland/runtime/ime/trierarch-ime.sock")
}
