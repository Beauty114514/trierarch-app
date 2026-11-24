package app.trierarch.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSnapshotTest {
    @Test
    fun stoppedProfilesHaveNoSession() {
        val snapshot = buildRuntimeSnapshot(
            running = emptySet(),
            active = null,
            pendingId = null,
            pendingStopping = false,
        )

        assertTrue(snapshot.sessions.isEmpty())
        assertNull(snapshot.pending)
    }

    @Test
    fun aRunningSessionIsActiveWhenTheTerminalShowsIt() {
        val snapshot = buildRuntimeSnapshot(setOf("arch"), "arch", null, false)

        val session = snapshot.sessions.getValue("arch")
        assertEquals(SessionPhase.RUNNING, session.phase)
        assertTrue(session.active)
        assertNull(snapshot.pending)
    }

    @Test
    fun aRunningSessionIsNotActiveWhenAnotherSurfaceIsShown() {
        val snapshot = buildRuntimeSnapshot(setOf("arch"), null, null, false)

        assertFalse(snapshot.sessions.getValue("arch").active)
    }

    @Test
    fun aPendingStartHasNoSessionYet() {
        val snapshot = buildRuntimeSnapshot(emptySet(), null, "arch", false)

        assertEquals(SessionPhase.STARTING, snapshot.sessions.getValue("arch").phase)
        assertEquals("arch", snapshot.pending)
    }

    @Test
    fun aPendingStopKeepsTheRunningSession() {
        val snapshot = buildRuntimeSnapshot(setOf("arch"), "arch", "arch", true)

        assertEquals(SessionPhase.STOPPING, snapshot.sessions.getValue("arch").phase)
        assertEquals("arch", snapshot.pending)
    }

    @Test
    fun anotherProfilesPendingActionDoesNotHideARunningSession() {
        val snapshot = buildRuntimeSnapshot(setOf("arch"), null, "kde", false)

        assertEquals(SessionPhase.RUNNING, snapshot.sessions.getValue("arch").phase)
        assertEquals(SessionPhase.STARTING, snapshot.sessions.getValue("kde").phase)
        assertEquals("kde", snapshot.pending)
    }
}
