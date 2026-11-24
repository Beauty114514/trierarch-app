package app.trierarch.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlProtocolTest {
    private val token = "token"

    @Test
    fun parsesUiOpen() {
        assertEquals(RuntimeCommand.OpenUi, ControlProtocol.parse(token, "$token\tui.open"))
    }

    @Test
    fun rejectsUiOpenWithArguments() {
        assertNull(ControlProtocol.parse(token, "$token\tui.open\tarch-plasma"))
    }

    @Test
    fun refusesRequestsWithoutTheToken() {
        assertNull(ControlProtocol.parse(token, "wrong\tui.open"))
        assertNull(ControlProtocol.parse("", "$token\tui.open"))
    }

    @Test
    fun keepsTheExistingCommands() {
        assertEquals(RuntimeCommand.Status(null), ControlProtocol.parse(token, "$token\tstatus"))
        assertEquals(RuntimeCommand.Status("a"), ControlProtocol.parse(token, "$token\tstatus\ta"))
        assertEquals(RuntimeCommand.Run("a"), ControlProtocol.parse(token, "$token\trun\ta"))
        assertEquals(RuntimeCommand.Stop(null), ControlProtocol.parse(token, "$token\tstop"))
        assertEquals(RuntimeCommand.Stop("a"), ControlProtocol.parse(token, "$token\tstop\ta"))
        assertEquals(RuntimeCommand.Rerun("a"), ControlProtocol.parse(token, "$token\trerun\ta"))
    }

    @Test
    fun rejectsMalformedRequests() {
        assertNull(ControlProtocol.parse(token, null))
        assertNull(ControlProtocol.parse(token, ""))
        assertNull(ControlProtocol.parse(token, "$token"))
        assertNull(ControlProtocol.parse(token, "$token\trun"))
        assertNull(ControlProtocol.parse(token, "$token\trun\ta\tb"))
    }
}
