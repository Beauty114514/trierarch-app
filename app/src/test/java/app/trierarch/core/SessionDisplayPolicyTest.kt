package app.trierarch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionDisplayPolicyTest {
    @Test
    fun terminalSessionsNeverConflict() {
        assertNull(findDisplayConflict(mapOf("a" to "none"), "b", "none"))
    }

    @Test
    fun aSecondWaylandSessionConflicts() {
        assertEquals("arch", findDisplayConflict(mapOf("arch" to "wayland"), "kali", "wayland"))
    }

    @Test
    fun aSecondX11SessionConflicts() {
        assertEquals("kali", findDisplayConflict(mapOf("kali" to "x11"), "arch", "x11"))
    }

    @Test
    fun differentDisplayHostsDoNotConflict() {
        assertNull(findDisplayConflict(mapOf("arch" to "wayland"), "kali", "x11"))
    }

    @Test
    fun aSessionDoesNotConflictWithItself() {
        assertNull(findDisplayConflict(mapOf("arch" to "wayland"), "arch", "wayland"))
    }
}
