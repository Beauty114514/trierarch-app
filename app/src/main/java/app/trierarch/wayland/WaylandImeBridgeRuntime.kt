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
        // DroidSpaces binds this app-private directory into the guest without
        // changing its Android UID. The guest bridge must both traverse it and
        // create its control socket here.
        check(directory.setReadable(true, false)) { "Unable to make Wayland IME runtime readable" }
        check(directory.setWritable(true, false)) { "Unable to make Wayland IME runtime writable" }
        check(directory.setExecutable(true, false)) { "Unable to make Wayland IME runtime traversable" }
        val destination = File(directory, fileName)
        // This guest executable changes independently of the Android app's
        // Kotlin classes. Always replace a previous APK asset atomically so an
        // app upgrade cannot retain an obsolete bridge binary.
        val temporary = File(directory, ".${fileName}.${System.nanoTime()}.tmp")
        context.assets.open(assetPath).use { input ->
            temporary.outputStream().use(input::copyTo)
        }
        check(temporary.renameTo(destination)) { "Unable to install Wayland IME bridge" }
        check(destination.setReadable(true, false)) { "Unable to make Wayland IME bridge readable" }
        check(destination.setExecutable(true, false)) { "Unable to mark Wayland IME bridge executable" }
        return destination
    }

    fun socket(context: Context): File = File(context.filesDir, "wayland/runtime/ime/trierarch-ime.sock")
}
