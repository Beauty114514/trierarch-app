package app.trierarch.audio

import android.content.Context
import app.trierarch.nativebridge.NativePtyBridge
import java.io.File
import java.io.FileOutputStream

/** Deploys and supervises the Android PipeWire playback service for DroidSpaces. */
object PipeWireHostController {
    private const val ASSET_ROOT = "pipewire/arm64-v8a"
    @Synchronized
    fun start(context: Context): File {
        check(context.assets.list(ASSET_ROOT).orEmpty().isNotEmpty()) {
            "PipeWire Android runtime is not packaged; build trierarch-packages/pipewire-host first"
        }
        val payload = File(context.filesDir, "pipewire")
        copyTree(context, ASSET_ROOT, payload)
        val runtime = File(context.filesDir, "pipewire-run").apply { mkdirs() }
        check(runtime.isDirectory) { "Unable to create the PipeWire runtime directory" }
        NativePtyBridge.startPipeWireHost(
            runtime.absolutePath,
            payload.absolutePath,
            context.applicationInfo.nativeLibraryDir,
        )
        return runtime
    }

    @Synchronized
    fun stop() = NativePtyBridge.stopPipeWireHost()

    private fun copyTree(context: Context, assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            return
        }
        check(destination.isDirectory || destination.mkdirs()) {
            "Unable to create PipeWire payload directory: $destination"
        }
        children.forEach { child -> copyTree(context, "$assetPath/$child", File(destination, child)) }
    }
}
