package app.trierarch.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsProfileTest {
    @Test
    fun adrenoUsesThePrivateMesaSelectorAndKgslDriver() {
        val environment = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_ADRENO,
            qtQuickBackend = ProfileStore.QT_QUICK_SOFTWARE,
        ).environment()

        assertTrue(environment.contains("TRIERARCH_ADRENO_MESA=/opt/trierarch/mesa/adreno/current"))
        assertTrue(environment.contains("LD_LIBRARY_PATH=/opt/trierarch/mesa/adreno/current/lib"))
        assertTrue(environment.contains("LIBGL_DRIVERS_PATH=/opt/trierarch/mesa/adreno/current/lib/dri"))
        assertTrue(environment.contains("GALLIUM_DRIVER=freedreno"))
        assertTrue(environment.contains("MESA_LOADER_DRIVER_OVERRIDE=kgsl"))
        assertTrue(environment.contains("FD_FORCE_KGSL=1"))
        assertTrue(environment.contains("KWIN_RENDER_NODES=/dev/dri/renderD128"))
        assertTrue(environment.contains("QT_QUICK_BACKEND=software"))
        assertFalse(environment.any { it.startsWith("LIBGL_ALWAYS_SOFTWARE=") })
    }
}
