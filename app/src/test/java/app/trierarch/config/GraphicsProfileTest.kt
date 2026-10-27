package app.trierarch.config

import org.junit.Assert.assertEquals
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

    @Test
    fun venusSelectsTheVtestTransportWithoutForcingAGalliumDriver() {
        val environment = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_VENUS,
            qtQuickBackend = ProfileStore.GRAPHICS_AUTO,
        ).environment()

        assertTrue(environment.contains("VN_DEBUG=vtest"))
        assertFalse(environment.any { it.startsWith("GALLIUM_DRIVER=") })
        assertFalse(environment.any { it.startsWith("MESA_LOADER_DRIVER_OVERRIDE=") })
        assertFalse(environment.any { it.startsWith("LIBGL_ALWAYS_SOFTWARE=") })
    }

    @Test
    fun virglUsesTheSoftwareDriFrontendAndNeverTheLoaderOverride() {
        val profile = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_VIRGL,
            qtQuickBackend = ProfileStore.GRAPHICS_AUTO,
        )
        val environment = profile.environment()

        // virpipe is a Gallium driver selected through the software DRI
        // frontend; `MESA_LOADER_DRIVER_OVERRIDE=virpipe` is not a DRI name and
        // makes eglInitialize() fail.
        assertTrue(environment.contains("LIBGL_ALWAYS_SOFTWARE=1"))
        assertTrue(environment.contains("GALLIUM_DRIVER=virpipe"))
        assertFalse(environment.contains("MESA_LOADER_DRIVER_OVERRIDE=virpipe"))
        assertFalse(environment.any { it.startsWith("MESA_LOADER_DRIVER_OVERRIDE=") })
    }

    @Test
    fun vtestTransportsResolveToShmAndAskForSoftwareCompositors() {
        val virgl = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_VIRGL,
            qtQuickBackend = ProfileStore.GRAPHICS_AUTO,
        )
        assertEquals(ProfileStore.BUFFER_SHM, virgl.resolvedBuffer)
        assertTrue(virgl.environment().contains("KWIN_COMPOSE=Q"))
        assertTrue(virgl.environment().contains("WLR_RENDERER=pixman"))
        assertTrue(virgl.environment().contains("TRIERARCH_BUFFER=shm"))
        assertTrue(virgl.environment().contains("TRIERARCH_RENDERER=virgl"))

        val venus = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_VENUS,
            qtQuickBackend = ProfileStore.GRAPHICS_AUTO,
        )
        assertEquals(ProfileStore.BUFFER_SHM, venus.resolvedBuffer)
    }

    @Test
    fun deviceBackedRenderersKeepDmabufUnlessOverridden() {
        val auto = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_LLVMPIPE,
            qtQuickBackend = ProfileStore.QT_QUICK_SOFTWARE,
        )
        assertEquals(ProfileStore.BUFFER_DMABUF, auto.resolvedBuffer)
        assertFalse(auto.environment().contains("KWIN_COMPOSE=Q"))

        val shm = ProfileStore.GraphicsProfile(
            renderer = ProfileStore.GRAPHICS_LLVMPIPE,
            qtQuickBackend = ProfileStore.QT_QUICK_SOFTWARE,
            buffer = ProfileStore.BUFFER_SHM,
        )
        assertEquals(ProfileStore.BUFFER_SHM, shm.resolvedBuffer)
        assertTrue(shm.environment().contains("KWIN_COMPOSE=Q"))
        assertTrue(shm.environment().contains("WLR_RENDERER=pixman"))
    }
}
