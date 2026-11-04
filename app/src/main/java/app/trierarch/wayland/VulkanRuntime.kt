package app.trierarch.wayland

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Production entry point for the host Vulkan renderer.
 *
 * The driver is still supplied outside the APK during bring-up.  Once the
 * pinned Turnip asset is added, the same code extracts it from assets and then
 * configures the native presenter before the first Wayland surface is attached.
 */
object VulkanRuntime {
    private const val TAG = "TrierarchVulkan"
    private const val DRIVER_ASSET = "turnip/vulkan.ad07xx.so"

    private val lock = Any()
    private var attempted = false
    private var enabled = false

    fun ensureConfigured(context: Context): Boolean {
        synchronized(lock) {
            if (attempted) return enabled
            attempted = true
            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            val adrenotools = File(nativeDir, "libadrenotools.so")
            val driverDir = File(context.filesDir, "vulkan/driver")
            val driver = File(driverDir, "vulkan.ad07xx.so")
            if (!driver.isFile) {
                driverDir.mkdirs()
                extractDriver(context, driver)
            }
            if (!adrenotools.isFile || !driver.isFile) {
                Log.w(TAG, "Vulkan runtime unavailable: adrenotools=${adrenotools.isFile} driver=${driver.isFile}")
                return false
            }
            val tmpDir = File(context.cacheDir, "vulkan").apply { mkdirs() }
            WaylandBridge.configureVulkan(
                adrenotoolsLibrary = adrenotools.absolutePath,
                driverDir = driverDir.absolutePath + "/",
                hookDir = nativeDir.absolutePath + "/",
                tmpDir = tmpDir.absolutePath + "/",
            )
            enabled = true
            Log.i(TAG, "Vulkan runtime configured driver=${driver.absolutePath}")
            return true
        }
    }

    private fun extractDriver(context: Context, target: File) {
        runCatching {
            context.assets.open(DRIVER_ASSET).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }.onFailure { Log.w(TAG, "Turnip asset not available", it) }
    }
}
