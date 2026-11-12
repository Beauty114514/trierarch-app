package app.trierarch.terminal

import app.trierarch.config.ProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeBootstrapTest {
    private class FakeHosts : RuntimeHosts {
        val calls = mutableListOf<String>()

        override fun startVirgl(venus: Boolean): String {
            calls += "virgl:$venus"
            return "/virgl"
        }

        override fun startWayland(bufferMode: String, desktopScalePercent: Int) {
            calls += "wayland:$bufferMode:$desktopScalePercent"
        }

        override fun startPipeWire(): String {
            calls += "pipewire"
            return "/pipewire"
        }

        override fun stopWayland() {
            calls += "stop-wayland"
        }

        override fun stopVirgl() {
            calls += "stop-virgl"
        }

        override fun stopPipeWire() {
            calls += "stop-pipewire"
        }

        override fun installGbmShim(waylandRuntimeDirectory: String) {
            calls += "gbm:$waylandRuntimeDirectory"
        }
    }

    private class FakePaths : RuntimePaths {
        override fun x11SocketDirectory(display: String): String? =
            if (display == ProfileStore.DISPLAY_X11) "/x11" else null

        override fun waylandRuntimeDirectory(display: String): String? =
            if (display == ProfileStore.DISPLAY_WAYLAND) "/wayland" else null

        override fun waylandImeBridge(display: String): String? =
            if (display == ProfileStore.DISPLAY_WAYLAND) "/ime" else null

        override fun udevCompatibilityLibrary(enabled: Boolean): String =
            if (enabled) "/compat" else ""
    }

    private fun spec(
        display: String = ProfileStore.DISPLAY_WAYLAND,
        renderer: String = ProfileStore.GRAPHICS_VIRGL,
        bufferMode: String = ProfileStore.BUFFER_DMABUF,
        audioBackend: String = ProfileStore.AUDIO_PIPEWIRE,
        installGbmShim: Boolean = true,
        desktopScalePercent: Int = 100,
    ) = RuntimeLaunchSpec(
        profileId = "profile",
        display = display,
        renderer = renderer,
        bufferMode = bufferMode,
        desktopScalePercent = desktopScalePercent,
        audioBackend = audioBackend,
        enablesUdevMonitorShim = true,
        installGbmShim = installGbmShim,
    )

    @Test
    fun startsHostsInOrderAndInstallsGbmShimForWaylandVirgl() {
        val hosts = FakeHosts()
        val bootstrap = RuntimeBootstrap(hosts, FakePaths())

        val resources = bootstrap.prepareHosts(spec())

        assertEquals(
            listOf("virgl:false", "wayland:dmabuf:100", "pipewire", "gbm:/wayland"),
            hosts.calls,
        )
        assertEquals("/virgl", resources.virglRuntimeDirectory)
        assertEquals("/pipewire", resources.pipewireRuntimeDirectory)
        assertEquals("/wayland", resources.waylandRuntimeDirectory)
    }

    @Test
    fun chrootDoesNotInstallGbmShim() {
        val hosts = FakeHosts()
        val bootstrap = RuntimeBootstrap(hosts, FakePaths())

        bootstrap.prepareHosts(spec(installGbmShim = false))

        assertEquals(listOf("virgl:false", "wayland:dmabuf:100", "pipewire"), hosts.calls)
    }

    @Test
    fun x11LlvmpipeDoesNotStartVirglWaylandOrGbmShim() {
        val hosts = FakeHosts()
        val bootstrap = RuntimeBootstrap(hosts, FakePaths())

        val resources = bootstrap.prepareHosts(
            spec(
                display = ProfileStore.DISPLAY_X11,
                renderer = ProfileStore.GRAPHICS_LLVMPIPE,
                bufferMode = ProfileStore.BUFFER_SHM,
                audioBackend = ProfileStore.AUDIO_OFF,
            ),
        )

        assertEquals(emptyList<String>(), hosts.calls)
        assertEquals("", resources.virglRuntimeDirectory)
        assertEquals("", resources.pipewireRuntimeDirectory)
        assertNull(resources.waylandRuntimeDirectory)
    }

    @Test
    fun finishResourcesAddsDisplayAndCompatibilityPaths() {
        val bootstrap = RuntimeBootstrap(FakeHosts(), FakePaths())
        val resources = bootstrap.prepareHosts(spec())
        val finished = bootstrap.finishResources(spec(), resources)

        assertNull(finished.x11SocketDirectory)
        assertEquals("/ime", finished.waylandImeBridge)
        assertEquals("/compat", finished.udevCompatibilityLibrary)
    }

    @Test
    fun stopHelpersForwardToHosts() {
        val hosts = FakeHosts()
        val bootstrap = RuntimeBootstrap(hosts, FakePaths())

        bootstrap.stopWayland()
        bootstrap.stopVirgl()
        bootstrap.stopPipeWire()

        assertEquals(listOf("stop-wayland", "stop-virgl", "stop-pipewire"), hosts.calls)
    }
}
