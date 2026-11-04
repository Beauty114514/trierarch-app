package app.trierarch.wayland

import android.content.Context
import android.util.Log
import android.view.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Development-only trigger for the production Vulkan presenter skeleton.
 *
 * Create files/vulkan-self-test and place the external AdrenoTools Turnip
 * driver under files/vulkan/ to run one swapchain smoke test on the next
 * Wayland surface creation.
 */
object VulkanSelfTest {
    private const val TAG = "TrierarchVulkan"
    private const val TRIGGER = "vulkan-self-test"
    private val ran = AtomicBoolean(false)

    fun runIfRequested(context: Context, surface: Surface) {
        if (!File(context.filesDir, TRIGGER).isFile) return
        if (!ran.compareAndSet(false, true)) return
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val adrenotools = File(nativeDir, "libadrenotools.so")
        val driverDir = File(context.filesDir, "vulkan/driver")
        val hookDir = nativeDir
        val tmpDir = File(context.cacheDir, "vulkan").apply { mkdirs() }
        if (!adrenotools.isFile || !driverDir.isDirectory) {
            Log.w(TAG, "Vulkan self-test assets are incomplete")
            return
        }
        val ok = runCatching {
            WaylandBridge.vulkanSelfTest(
                surface = surface,
                adrenotoolsLibrary = adrenotools.absolutePath,
                driverDir = driverDir.absolutePath + "/",
                hookDir = hookDir.absolutePath + "/",
                tmpDir = tmpDir.absolutePath + "/",
            )
        }.onFailure { Log.e(TAG, "Vulkan self-test threw", it) }.getOrDefault(false)
        Log.i(TAG, "Vulkan self-test result=$ok")
        File(context.filesDir, TRIGGER).delete()
    }
}
