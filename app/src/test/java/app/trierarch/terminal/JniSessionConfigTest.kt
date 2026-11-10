package app.trierarch.terminal

import app.trierarch.config.ProfileStore
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
        val profile = ProfileStore.DroidspacesProfile(
            id = "droid",
            container = "trierarch",
            user = "root",
            display = ProfileStore.DISPLAY_WAYLAND,
            desktopScale = ProfileStore.DESKTOP_SCALE_DEFAULT,
            launchArgv = listOf("startplasma-wayland"),
            graphics = ProfileStore.GraphicsProfile(
                renderer = ProfileStore.GRAPHICS_ADRENO,
                qtQuickBackend = ProfileStore.QT_QUICK_SOFTWARE,
            ),
            compatibility = ProfileStore.CompatibilityProfile(
                udevMonitor = ProfileStore.COMPAT_OFF,
            ),
            audioBackend = ProfileStore.AUDIO_OFF,
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
