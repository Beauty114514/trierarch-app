package app.trierarch.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProotPacmanEnvironmentTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagesPackagesInsideTheRootfsAndCleansThemUp() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        val nativeLibraries = temporaryFolder.newFolder("native")
        val cache = temporaryFolder.newFolder("cache")
        val packages = temporaryFolder.newFolder("packages")
        File(packages, "mesa-1:27.0.0-91-aarch64.pkg.tar.xz").writeText("mesa")
        File(packages, "vulkan-freedreno-1:27.0.0-91-aarch64.pkg.tar.xz").writeText("vulkan")
        val environment = ProotPacmanEnvironment(rootfs, nativeLibraries, cache)

        val guest = environment.stagePackages(packages)

        assertTrue(guest.startsWith("/var/tmp/trierarch-components-"))
        val host = File(rootfs, guest.removePrefix("/"))
        assertEquals(
            listOf(
                "mesa-1:27.0.0-91-aarch64.pkg.tar.xz",
                "vulkan-freedreno-1:27.0.0-91-aarch64.pkg.tar.xz",
            ).sorted(),
            host.listFiles().orEmpty().map { it.name }.sorted(),
        )

        environment.cleanup(guest)
        assertTrue(!host.exists())
    }
}
