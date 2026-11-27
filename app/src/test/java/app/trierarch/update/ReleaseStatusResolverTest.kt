package app.trierarch.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseStatusResolverTest {
    private fun release(version: String): ComponentRelease = ComponentRelease(
        id = "mesa",
        name = "Mesa",
        version = version,
        file = "mesa-$version.tar.xz",
        url = "https://example.com/mesa-$version.tar.xz",
        size = 1,
        sha256 = "a".repeat(64),
        install = ComponentInstall(
            type = ComponentInstallType.PACMAN,
            packages = listOf(ComponentPackage("mesa", "1:$version")),
        ),
    )

    private val mesa25 = release("25.0.0-1")
    private val mesa26 = release("26.0.0-1")
    private val receipt25 = InstallReceipt(
        target = "droidspaces:Arch",
        componentId = "mesa",
        releaseVersion = "25.0.0-1",
        packages = mapOf("mesa" to "1:25.0.0-1"),
    )

    @Test
    fun installed25AndDownloaded26() {
        val snapshot = PackageSnapshot(installed = mapOf("mesa" to "1:25.0.0-1"))
        val older = ReleaseStatusResolver.resolve(mesa25, true, receipt25, snapshot)
        val newer = ReleaseStatusResolver.resolve(mesa26, true, null, snapshot)
        assertEquals(ReleasePhase.INSTALLED, older.phase)
        assertTrue(older.canUninstall)
        assertEquals(ReleasePhase.MISMATCH, newer.phase)
        assertTrue(newer.canInstall)
    }

    @Test
    fun installing26Makes25AMismatch() {
        val snapshot = PackageSnapshot(installed = mapOf("mesa" to "1:26.0.0-1"))
        val receipt = InstallReceipt(
            target = "droidspaces:Arch",
            componentId = "mesa",
            releaseVersion = "26.0.0-1",
            packages = mapOf("mesa" to "1:26.0.0-1"),
        )
        val older = ReleaseStatusResolver.resolve(mesa25, true, receipt25, snapshot)
        val newer = ReleaseStatusResolver.resolve(mesa26, true, receipt, snapshot)
        assertEquals(ReleasePhase.MISMATCH, older.phase)
        assertTrue(older.canInstall)
        assertEquals(ReleasePhase.INSTALLED, newer.phase)
        assertTrue(newer.canUninstall)
    }

    @Test
    fun externalPackagesAreNotOwned() {
        val snapshot = PackageSnapshot(installed = mapOf("mesa" to "1:26.0.0-1"))
        val status = ReleaseStatusResolver.resolve(mesa26, true, null, snapshot)
        assertEquals(ReleasePhase.EXTERNAL, status.phase)
        assertFalse(status.owned)
        assertFalse(status.canUninstall)
    }

    @Test
    fun requiredPackagesCannotBeRemoved() {
        val snapshot = PackageSnapshot(
            installed = mapOf("mesa" to "1:26.0.0-1"),
            requiredBy = mapOf("mesa" to listOf("hyprland", "kwin")),
        )
        val receipt = InstallReceipt(
            target = "droidspaces:Arch",
            componentId = "mesa",
            releaseVersion = "26.0.0-1",
            packages = mapOf("mesa" to "1:26.0.0-1"),
        )
        val status = ReleaseStatusResolver.resolve(mesa26, true, receipt, snapshot)
        assertEquals(ReleasePhase.INSTALLED, status.phase)
        assertEquals(setOf("hyprland", "kwin"), status.requiredBy)
        assertFalse(status.removable)
        assertFalse(status.canUninstall)
    }

    @Test
    fun partialInstallationIsReported() {
        val releaseWithTwo = mesa26.copy(
            install = mesa26.install.copy(
                packages = listOf(
                    ComponentPackage("mesa", "1:26.0.0-1"),
                    ComponentPackage("vulkan-freedreno", "1:26.0.0-1"),
                ),
            ),
        )
        val snapshot = PackageSnapshot(installed = mapOf("mesa" to "1:26.0.0-1"))
        val status = ReleaseStatusResolver.resolve(releaseWithTwo, true, null, snapshot)
        assertEquals(ReleasePhase.PARTIAL, status.phase)
    }
}
