package app.trierarch.feature.profiles

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileTemplateTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun rootfs(): File = temporaryFolder.newFolder("alpine").apply {
        File(this, "etc").mkdirs()
        File(this, "etc/os-release").writeText("ID=alpine\n")
        File(this, "bin").mkdirs()
        File(this, "bin/sh").writeText("")
    }

    @Test
    fun prootTemplateUsesRootfsAndShell() {
        val rootfs = rootfs()
        val draft = ProfileDraft.Proot(
            id = "alpine-demo",
            rootfs = rootfs.absolutePath,
            shell = "/bin/sh",
            display = ProfileDraft.DISPLAY_X11,
        )

        assertNull(validateProfileDraft(draft))
        val text = draft.toToml()
        assertTrue(text.contains("id = \"alpine-demo\""))
        assertTrue(text.contains("runtime = \"proot\""))
        assertTrue(text.contains("rootfs = \"${rootfs.absolutePath}\""))
        assertTrue(text.contains("shell = \"/bin/sh\""))
        assertTrue(text.contains("type = \"x11\""))
    }

    @Test
    fun chrootTemplateUsesChrootRuntime() {
        val rootfs = rootfs()
        val text = ProfileDraft.Chroot(
            id = "alpine-chroot",
            rootfs = rootfs.absolutePath,
            shell = "/bin/sh",
        ).toToml()

        assertTrue(text.contains("runtime = \"chroot\""))
        assertTrue(text.contains("rootfs = \"${rootfs.absolutePath}\""))
    }

    @Test
    fun droidspacesTemplateHasNoRootfsOrShell() {
        val draft = ProfileDraft.Droidspaces(
            id = "arch-ds",
            container = "Arch",
            user = "",
        )

        assertNull(validateProfileDraft(draft))
        val text = draft.toToml()
        assertTrue(text.contains("runtime = \"droidspaces\""))
        assertTrue(text.contains("container = \"Arch\""))
        assertTrue(text.contains("user = \"root\""))
        assertFalse(text.contains("rootfs"))
        assertFalse(text.contains("shell"))
    }

    @Test
    fun validationRejectsMissingShellAndEmptyContainer() {
        val rootfs = rootfs()
        assertEquals(
            "configured shell does not exist in rootfs: /bin/bash",
            validateProfileDraft(
                ProfileDraft.Proot("alpine-demo", rootfs.absolutePath, "/bin/bash"),
            ),
        )
        assertTrue(
            validateProfileDraft(
                ProfileDraft.Droidspaces("arch-ds", "", "root"),
            )?.contains("container") == true,
        )
    }

    private fun assertEquals(expected: String, actual: String?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
