package app.trierarch.runtime

import app.trierarch.core.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlProtocolTest {
    private val token = "token"

    @Test
    fun parsesGuiOpen() {
        assertEquals(
            ControlProtocol.Request.Execute(Command.OpenGui),
            ControlProtocol.parse(token, "$token\tgui.open"),
        )
    }

    @Test
    fun rejectsGuiOpenWithArguments() {
        assertNull(ControlProtocol.parse(token, "$token\tgui.open\tarch"))
    }

    @Test
    fun refusesRequestsWithoutTheToken() {
        assertNull(ControlProtocol.parse(token, "wrong\tgui.open"))
        assertNull(ControlProtocol.parse("", "$token\tgui.open"))
    }

    @Test
    fun parsesStatusAsAQuery() {
        assertEquals(ControlProtocol.Request.Status(null), ControlProtocol.parse(token, "$token\tstatus"))
        assertEquals(ControlProtocol.Request.Status("a"), ControlProtocol.parse(token, "$token\tstatus\ta"))
    }

    @Test
    fun parsesSessionCommands() {
        assertEquals(
            ControlProtocol.Request.Execute(Command.Run("a")),
            ControlProtocol.parse(token, "$token\trun\ta"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Open("a")),
            ControlProtocol.parse(token, "$token\topen\ta"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Stop(null)),
            ControlProtocol.parse(token, "$token\tstop"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Stop("a")),
            ControlProtocol.parse(token, "$token\tstop\ta"),
        )
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
