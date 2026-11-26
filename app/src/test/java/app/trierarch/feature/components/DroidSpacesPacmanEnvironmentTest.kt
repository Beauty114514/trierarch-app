package app.trierarch.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DroidSpacesPacmanEnvironmentTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagesPackagesInAppStorage() {
        val cache = temporaryFolder.newFolder("cache")
        val packages = temporaryFolder.newFolder("packages")
        File(packages, "mesa-1:27.0.0-91-aarch64.pkg.tar.xz").writeText("mesa")
        val environment = DroidSpacesPacmanEnvironment("Arch", cache)

        val guest = environment.stagePackages(packages)

        assertTrue(guest.startsWith("/var/tmp/trierarch-components-"))
        val staged = File(cache, guest.removePrefix("/var/tmp/"))
        assertEquals(listOf("mesa-1:27.0.0-91-aarch64.pkg.tar.xz"), staged.listFiles().orEmpty().map { it.name })
        environment.cleanup(guest)
        assertTrue(!staged.exists())
    }
}
