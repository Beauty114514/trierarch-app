package app.trierarch.compat

import android.content.Context
import java.io.File

/** Extracts the guest-glibc GBM token shim from the APK.  It is not an Android JNI library. */
object GbmShimRuntime {
    private const val ASSET_PATH = "gbm-shim/arm64-v8a/libgbm-shim.so"
    private const val FILE_NAME = "libgbm-shim.so"

    /**
     * Installs the shim into the host Wayland runtime directory.  That directory
     * is bind-mounted into the guest as /tmp/trierarch-wayland, so KWin can
     * preload it by the stable guest path.
     */
    fun install(context: Context, waylandRuntimeDirectory: File): File {
        check(waylandRuntimeDirectory.isDirectory || waylandRuntimeDirectory.mkdirs()) {
            "Unable to create Wayland runtime directory"
        }
        val destination = File(waylandRuntimeDirectory, FILE_NAME)
        val temporary = File(waylandRuntimeDirectory, ".$FILE_NAME.${System.nanoTime()}.tmp")
        context.assets.open(ASSET_PATH).use { input ->
            temporary.outputStream().use(input::copyTo)
        }
        check(temporary.renameTo(destination)) { "Unable to install GBM shim" }
        check(destination.setExecutable(true, false)) { "Unable to make GBM shim executable" }
        return destination
    }
}
