package app.trierarch.input

import android.content.Context
import java.io.File

/** Installs the root-only physical keyboard reader packaged in the APK. */
object RootInputReaderRuntime {
    private const val assetPath = "input-reader/arm64-v8a/trierarch-input-reader"
    private const val fileName = "trierarch-input-reader"

    fun executable(context: Context): File {
        val directory = File(context.filesDir, "input")
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create root input runtime directory"
        }
        val destination = File(directory, fileName)
        val temporary = File(directory, ".$fileName.${System.nanoTime()}.tmp")
        context.assets.open(assetPath).use { input ->
            temporary.outputStream().use(input::copyTo)
        }
        check(temporary.renameTo(destination)) { "Unable to install root input reader" }
        check(destination.setReadable(true, false)) {
            "Unable to make root input reader readable"
        }
        check(destination.setExecutable(true, false)) {
            "Unable to mark root input reader executable"
        }
        return destination
    }
}
