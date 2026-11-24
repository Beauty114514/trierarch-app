package app.trierarch.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlProtocolTest {
    private val token = "token"

    @Test
    fun parsesGuiOpen() {
        assertEquals(RuntimeCommand.OpenGui, ControlProtocol.parse(token, "$token\tgui.open"))
    }

    @Test
    fun rejectsGuiOpenWithArguments() {
        assertNull(ControlProtocol.parse(token, "$token\tgui.open\tarch-plasma"))
    }

    @Test
    fun refusesRequestsWithoutTheToken() {
        assertNull(ControlProtocol.parse(token, "wrong\tgui.open"))
        assertNull(ControlProtocol.parse("", "$token\tgui.open"))
    }

    @Test
    fun keepsTheExistingCommands() {
        assertEquals(RuntimeCommand.Status(null), ControlProtocol.parse(token, "$token\tstatus"))
        assertEquals(RuntimeCommand.Status("a"), ControlProtocol.parse(token, "$token\tstatus\ta"))
        assertEquals(RuntimeCommand.Run("a"), ControlProtocol.parse(token, "$token\trun\ta"))
        assertEquals(RuntimeCommand.Stop(null), ControlProtocol.parse(token, "$token\tstop"))
        assertEquals(RuntimeCommand.Stop("a"), ControlProtocol.parse(token, "$token\tstop\ta"))
        assertEquals(RuntimeCommand.OpenSession("a"), ControlProtocol.parse(token, "$token\topen\ta"))
    }

    @Test
    fun rejectsMalformedRequests() {
        assertNull(ControlProtocol.parse(token, null))
        assertNull(ControlProtocol.parse(token, ""))
        assertNull(ControlProtocol.parse(token, "$token"))
        assertNull(ControlProtocol.parse(token, "$token\trun"))
        assertNull(ControlProtocol.parse(token, "$token\topen"))
        assertNull(ControlProtocol.parse(token, "$token\trun\ta\tb"))
    }
}
