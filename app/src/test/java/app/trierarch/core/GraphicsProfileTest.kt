package app.trierarch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsProfileTest {
    @Test
    fun adrenoKeepsOnlyTheKgslHintsSharedByFreedrenoAndZink() {
        val environment = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_ADRENO,
            qtQuickBackend = ProfileService.QT_QUICK_SOFTWARE,
        ).environment()

        assertTrue(environment.contains("TURNIP_KMD=kgsl"))
        assertTrue(environment.contains("FD_FORCE_KGSL=1"))
        assertTrue(environment.contains("TRIERARCH_RENDERER=adreno"))
        assertTrue(environment.contains("QT_QUICK_BACKEND=software"))
        // The launch command owns the Gallium/loader choice.
        assertFalse(environment.any { it.startsWith("GALLIUM_DRIVER=") })
        assertFalse(environment.any { it.startsWith("MESA_LOADER_DRIVER_OVERRIDE=") })
        assertFalse(environment.any { it.startsWith("TRIERARCH_ADRENO_MESA=") })
        assertFalse(environment.any { it.startsWith("LD_LIBRARY_PATH=") })
        assertFalse(environment.any { it.startsWith("LIBGL_ALWAYS_SOFTWARE=") })
    }

    @Test
    fun venusSelectsTheVtestTransportWithoutForcingAGalliumDriver() {
        val environment = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_VENUS,
            qtQuickBackend = ProfileService.GRAPHICS_AUTO,
        ).environment()

        assertTrue(environment.contains("VN_DEBUG=vtest"))
        assertFalse(environment.any { it.startsWith("GALLIUM_DRIVER=") })
        assertFalse(environment.any { it.startsWith("MESA_LOADER_DRIVER_OVERRIDE=") })
        assertFalse(environment.any { it.startsWith("LIBGL_ALWAYS_SOFTWARE=") })
    }

    @Test
    fun virglUsesTheSoftwareDriFrontendAndNeverTheLoaderOverride() {
        val profile = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_VIRGL,
            qtQuickBackend = ProfileService.GRAPHICS_AUTO,
        )
        val environment = profile.environment()

        // virpipe is a Gallium driver selected through the software DRI
        // frontend; `MESA_LOADER_DRIVER_OVERRIDE=virpipe` is not a DRI name and
        // makes eglInitialize() fail.
        assertTrue(environment.contains("LIBGL_ALWAYS_SOFTWARE=1"))
        assertTrue(environment.contains("GALLIUM_DRIVER=virpipe"))
        assertTrue(environment.contains(
            "TRIERARCH_TVG_SOCKET=/tmp/trierarch-wayland/tvgd.sock"
        ))
        assertFalse(environment.contains("MESA_LOADER_DRIVER_OVERRIDE=virpipe"))
        assertFalse(environment.any { it.startsWith("MESA_LOADER_DRIVER_OVERRIDE=") })
    }

    @Test
    fun virglDmabufPocLoadsTheBufferShim() {
        val profile = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_VIRGL,
            qtQuickBackend = ProfileService.GRAPHICS_AUTO,
            buffer = ProfileService.BUFFER_DMABUF,
        )

        assertEquals(ProfileService.BUFFER_DMABUF, profile.resolvedBuffer)
        assertTrue(profile.environment().contains(
            "LD_PRELOAD=/tmp/trierarch-wayland/libgbm-shim.so"
        ))
    }

    @Test
    fun vtestTransportsResolveToShmAndAskForSoftwareCompositors() {
        val virgl = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_VIRGL,
            qtQuickBackend = ProfileService.GRAPHICS_AUTO,
        )
        assertEquals(ProfileService.BUFFER_SHM, virgl.resolvedBuffer)
        assertTrue(virgl.environment().contains("KWIN_COMPOSE=Q"))
        assertTrue(virgl.environment().contains("WLR_RENDERER=pixman"))
        assertTrue(virgl.environment().contains("TRIERARCH_BUFFER=shm"))
        assertTrue(virgl.environment().contains("TRIERARCH_RENDERER=virgl"))

        val venus = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_VENUS,
            qtQuickBackend = ProfileService.GRAPHICS_AUTO,
        )
        assertEquals(ProfileService.BUFFER_SHM, venus.resolvedBuffer)
    }

    @Test
    fun deviceBackedRenderersKeepDmabufUnlessOverridden() {
        val auto = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_LLVMPIPE,
            qtQuickBackend = ProfileService.QT_QUICK_SOFTWARE,
        )
        assertEquals(ProfileService.BUFFER_DMABUF, auto.resolvedBuffer)
        assertFalse(auto.environment().contains("KWIN_COMPOSE=Q"))

        val shm = ProfileService.GraphicsProfile(
            renderer = ProfileService.GRAPHICS_LLVMPIPE,
            qtQuickBackend = ProfileService.QT_QUICK_SOFTWARE,
            buffer = ProfileService.BUFFER_SHM,
        )
        assertEquals(ProfileService.BUFFER_SHM, shm.resolvedBuffer)
        assertTrue(shm.environment().contains("KWIN_COMPOSE=Q"))
        assertTrue(shm.environment().contains("WLR_RENDERER=pixman"))
    }
}
