package app.trierarch.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PacmanTargetTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun buildsTheEnvironmentForEachRuntime() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        val native = temporaryFolder.newFolder("native")
        val cache = temporaryFolder.newFolder("cache")

        assertTrue(
            PacmanTarget.Proot("PRoot", rootfs, native, cache).environment()
                is ProotPacmanEnvironment,
        )
        assertTrue(
            PacmanTarget.Chroot("chroot", rootfs).environment()
                is ChrootPacmanEnvironment,
        )
        assertTrue(
            PacmanTarget.DroidSpaces("Arch", "Arch", cache).environment()
                is DroidSpacesPacmanEnvironment,
        )
    }
}
