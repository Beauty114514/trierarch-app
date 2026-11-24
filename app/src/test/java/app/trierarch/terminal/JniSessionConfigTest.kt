package app.trierarch.terminal

import app.trierarch.core.ProfileService
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JniSessionConfigTest {
    private fun fields(values: Array<String>): Map<String, String> =
        values.associate { value ->
            val separator = value.indexOf('=')
            value.substring(0, separator) to value.substring(separator + 1)
        }

    @Test
    fun prootConfigUsesNamedKeysAndIndexedLists() {
        val config = ProotSessionConfig(
            rootfs = File("/rootfs"),
            shell = "/bin/bash",
            nativeLibraryDirectory = File("/native"),
            cacheDirectory = File("/cache"),
            environment = SessionEnvironment(
                waylandRuntimeDirectory = File("/wayland"),
                udevCompatibilityLibrary = File("/compat.so"),
            ),
            launchArgv = listOf("startplasma-wayland", "--no-lockscreen"),
            graphicsEnvironment = listOf("TRIERARCH_BUFFER=dmabuf"),
        )

        val fields = fields(config.toJniConfig())

        assertEquals("/rootfs", fields["rootfs"])
        assertEquals("/bin/bash", fields["shell"])
        assertEquals("/wayland", fields["wayland_runtime_directory"])
        assertEquals("/compat.so", fields["udev_compatibility_library"])
        assertEquals("2", fields["launch_argv.count"])
        assertEquals("startplasma-wayland", fields["launch_argv.0"])
        assertEquals("--no-lockscreen", fields["launch_argv.1"])
        assertEquals("1", fields["graphics_environment.count"])
        assertEquals("TRIERARCH_BUFFER=dmabuf", fields["graphics_environment.0"])
    }

    @Test
    fun chrootConfigUsesSupervisorAndDiagnosticKeys() {
        val config = ChrootSessionConfig(
            rootfs = File("/guest"),
            shell = "/bin/bash",
            environment = SessionEnvironment(x11SocketDirectory = File("/x11")),
            chrootSupervisor = File("/supervisor"),
            launchArgv = emptyList(),
            graphicsEnvironment = emptyList(),
            diagnosticLog = File("/log"),
        )

        val fields = fields(config.toJniConfig())

        assertEquals("/guest", fields["rootfs"])
        assertEquals("/x11", fields["x11_socket_directory"])
        assertEquals("/supervisor", fields["chroot_supervisor"])
        assertEquals("0", fields["launch_argv.count"])
        assertEquals("0", fields["graphics_environment.count"])
    }

    @Test
    fun droidspacesConfigUsesProfileValues() {
        val profile = ProfileService.DroidspacesProfile(
            id = "droid",
            container = "trierarch",
            user = "root",
            display = ProfileService.DISPLAY_WAYLAND,
            desktopScale = ProfileService.DESKTOP_SCALE_DEFAULT,
            launchArgv = listOf("startplasma-wayland"),
            graphics = ProfileService.GraphicsProfile(
                renderer = ProfileService.GRAPHICS_ADRENO,
                qtQuickBackend = ProfileService.QT_QUICK_SOFTWARE,
            ),
            compatibility = ProfileService.CompatibilityProfile(
                udevMonitor = ProfileService.COMPAT_OFF,
            ),
            audioBackend = ProfileService.AUDIO_OFF,
        )

        val fields = fields(
            DroidspacesSessionConfig(
                profile = profile,
                environment = SessionEnvironment(),
                droidspacesSupervisor = File("/supervisor"),
            ).toJniConfig(),
        )

        assertEquals("trierarch", fields["container"])
        assertEquals("root", fields["user"])
        assertEquals("/supervisor", fields["droidspaces_supervisor"])
        assertEquals("startplasma-wayland", fields["launch_argv.0"])
        assertTrue(fields.getValue("graphics_environment.count").toInt() > 0)
    }
}
