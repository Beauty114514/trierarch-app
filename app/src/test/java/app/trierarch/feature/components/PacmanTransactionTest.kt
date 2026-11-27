package app.trierarch.feature.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PacmanTransactionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class FakeEnvironment(
        private val result: PacmanResult = PacmanResult("pacman", "", 0),
    ) : PacmanEnvironment {
        override val displayName = "fake"
        val commands = mutableListOf<List<String>>()
        var stagedDirectory: String? = null
        var cleaned: String? = null

        override fun stagePackages(packageDirectory: File): String {
            stagedDirectory = "/var/tmp/trierarch-components"
            return stagedDirectory!!
        }

        override fun runPacman(arguments: List<String>): PacmanResult {
            commands += arguments
            return result
        }

        override fun cleanup(stagingDirectory: String) {
            cleaned = stagingDirectory
        }
    }

    @Test
    fun installStagesAndCallsPacmanWithTheExtractedPackages() {
        val environment = FakeEnvironment()
        val transaction = PacmanTransaction(environment) { _, destination ->
            File(destination, "mesa-1:27.0.0-91-aarch64.pkg.tar.xz").writeText("a")
            File(destination, "vulkan-freedreno-1:27.0.0-91-aarch64.pkg.tar.xz").writeText("b")
        }
        val archive = File(temporaryFolder.newFolder("archive"), "mesa.tar.xz").apply {
            writeText("archive")
        }

        transaction.install(archive, listOf("mesa", "vulkan-freedreno"))

        assertEquals(
            listOf("pacman", "-U", "--noconfirm", "/var/tmp/trierarch-components/mesa-1:27.0.0-91-aarch64.pkg.tar.xz", "/var/tmp/trierarch-components/vulkan-freedreno-1:27.0.0-91-aarch64.pkg.tar.xz"),
            environment.commands.single(),
        )
        assertEquals("/var/tmp/trierarch-components", environment.cleaned)
    }

    @Test
    fun queryParsesInstalledVersions() {
        val environment = FakeEnvironment(
            PacmanResult("pacman", "mesa 1:27.0.0-91\n", 0),
        )
        val transaction = PacmanTransaction(environment)

        assertEquals(mapOf("mesa" to "1:27.0.0-91"), transaction.query(listOf("mesa")))
        assertEquals(listOf("pacman", "-Q", "mesa"), environment.commands.single())
    }

    @Test
    fun uninstallUsesOnlyTheDeclaredPackages() {
        val environment = FakeEnvironment()
        val transaction = PacmanTransaction(environment)

        transaction.uninstall(listOf("mesa", "vulkan-freedreno"))

        assertEquals(
            listOf("pacman", "-R", "--noconfirm", "mesa", "vulkan-freedreno"),
            environment.commands.single(),
        )
    }

    @Test
    fun installFailureCleansStagingAndThrows() {
        val environment = FakeEnvironment(PacmanResult("pacman", "error", 1))
        val transaction = PacmanTransaction(environment) { _, destination ->
            File(destination, "mesa-1.pkg.tar.zst").writeText("a")
        }
        val archive = File(temporaryFolder.newFolder("bad"), "mesa.tar.xz").apply {
            writeText("archive")
        }

        runCatching { transaction.install(archive, listOf("mesa")) }
            .onSuccess { throw AssertionError("install should fail") }
        assertEquals("/var/tmp/trierarch-components", environment.cleaned)
    }

    @Test
    fun queryReturnsEmptyWhenPacmanHasNoOutput() {
        val environment = FakeEnvironment(PacmanResult("pacman", "", 1))
        assertTrue(PacmanTransaction(environment).query(listOf("mesa")).isEmpty())
    }
}

class PacmanTransactionSnapshotTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class SequencedEnvironment(
        private val results: MutableList<PacmanResult>,
    ) : PacmanEnvironment {
        override val displayName = "sequenced"
        val commands = mutableListOf<List<String>>()

        override fun stagePackages(packageDirectory: File): String = "/var/tmp/components"
        override fun runPacman(arguments: List<String>): PacmanResult {
            commands += arguments
            return results.removeFirst()
        }
        override fun cleanup(stagingDirectory: String) = Unit
    }

    @Test
    fun snapshotParsesInstalledVersionsAndRequiredBy() {
        val environment = SequencedEnvironment(
            mutableListOf(
                PacmanResult("pacman", "mesa 1:27.0.0-91\n", 0),
                PacmanResult(
                    "pacman",
                    "Name            : mesa\n" +
                        "Version         : 1:27.0.0-91\n" +
                        "Required By     : hyprland kwin\n\n" +
                        "Name            : vulkan-freedreno\n" +
                        "Version         : 1:27.0.0-91\n" +
                        "Required By     : None\n",
                    0,
                ),
            ),
        )
        val transaction = PacmanTransaction(environment)

        val snapshot = transaction.snapshot(listOf("mesa", "vulkan-freedreno"))

        assertEquals(mapOf("mesa" to "1:27.0.0-91"), snapshot.installed)
        assertEquals(listOf("hyprland", "kwin"), snapshot.requiredBy["mesa"])
        assertEquals(emptyList<String>(), snapshot.requiredBy["vulkan-freedreno"])
        assertEquals(
            listOf(
                listOf("pacman", "-Q", "mesa", "vulkan-freedreno"),
                listOf("pacman", "-Qi", "mesa", "vulkan-freedreno"),
            ),
            environment.commands,
        )
    }
}
