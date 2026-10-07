package app.trierarch.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsProfileTest {
    @Test
    fun adrenoUsesFreedrenoThroughKgsl() {
        val environment = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_ADRENO,
            qtQuickBackend = ProfileStore.QT_QUICK_SOFTWARE,
        ).environment()

        assertTrue(environment.contains("GALLIUM_DRIVER=freedreno"))
        assertTrue(environment.contains("MESA_LOADER_DRIVER_OVERRIDE=kgsl"))
        assertTrue(environment.contains("TURNIP_KMD=kgsl"))
        assertTrue(environment.contains("FD_FORCE_KGSL=1"))
        assertTrue(environment.contains("QT_QUICK_BACKEND=software"))
        assertFalse(environment.any { it.startsWith("TRIERARCH_ADRENO_MESA=") })
        assertFalse(environment.any { it.startsWith("LD_LIBRARY_PATH=") })
        assertFalse(environment.any { it.startsWith("LIBGL_ALWAYS_SOFTWARE=") })
    }
}
