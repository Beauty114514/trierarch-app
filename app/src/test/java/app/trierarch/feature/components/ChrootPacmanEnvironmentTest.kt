package app.trierarch.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChrootPacmanEnvironmentTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagesPackagesUnderTheChrootRootfs() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        val packages = temporaryFolder.newFolder("packages")
        File(packages, "mesa-1:27.0.0-91-aarch64.pkg.tar.xz").writeText("mesa")
        val environment = ChrootPacmanEnvironment(rootfs)

        val guest = environment.stagePackages(packages)

        assertTrue(guest.startsWith("/var/tmp/trierarch-components-"))
        val host = File(rootfs, guest.removePrefix("/"))
        assertEquals(listOf("mesa-1:27.0.0-91-aarch64.pkg.tar.xz"), host.listFiles().orEmpty().map { it.name })
        environment.cleanup(guest)
        assertTrue(!host.exists())
    }
}
