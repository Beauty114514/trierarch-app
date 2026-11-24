package app.trierarch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionConflictsTest {
    private val arch = SessionSlot("arch", ProfileService.DISPLAY_WAYLAND, "droidspaces:arch")
    private val kali = SessionSlot("kali", ProfileService.DISPLAY_NONE, "proot:/rootfs/kali")

    @Test
    fun terminalSessionsNeverConflictOnDisplay() {
        assertNull(findDisplayConflict(listOf(kali), "next", ProfileService.DISPLAY_NONE))
    }

    @Test
    fun aSecondWaylandSessionConflicts() {
        assertEquals(arch, findDisplayConflict(listOf(arch), "kali", ProfileService.DISPLAY_WAYLAND))
    }

    @Test
    fun aSecondX11SessionConflicts() {
        val x11 = SessionSlot("arch", ProfileService.DISPLAY_X11, "droidspaces:arch")
        assertEquals(x11, findDisplayConflict(listOf(x11), "kali", ProfileService.DISPLAY_X11))
    }

    @Test
    fun differentDisplayHostsDoNotConflict() {
        assertNull(findDisplayConflict(listOf(arch), "kali", ProfileService.DISPLAY_X11))
    }

    @Test
    fun aSessionDoesNotConflictWithItself() {
        assertNull(findDisplayConflict(listOf(arch), "arch", ProfileService.DISPLAY_WAYLAND))
    }

    @Test
    fun twoProfilesCannotShareAContainer() {
        assertEquals(arch, findSourceConflict(listOf(arch), "kali", "droidspaces:arch"))
    }

    @Test
    fun twoProfilesCannotShareARootfs() {
        assertEquals(kali, findSourceConflict(listOf(kali), "arch", "proot:/rootfs/kali"))
    }

    @Test
    fun differentSourcesDoNotConflict() {
        assertNull(findSourceConflict(listOf(arch), "kali", "proot:/rootfs/kali"))
    }
}
