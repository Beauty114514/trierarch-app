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
            ControlProtocol.Request.Execute(Command.Session.OpenGui),
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
            ControlProtocol.Request.Execute(Command.Session.Run("a")),
            ControlProtocol.parse(token, "$token\trun\ta"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Session.Open("a")),
            ControlProtocol.parse(token, "$token\topen\ta"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Session.Stop(null)),
            ControlProtocol.parse(token, "$token\tstop"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Session.Stop("a")),
            ControlProtocol.parse(token, "$token\tstop\ta"),
        )
    }

    @Test
    fun parsesDocumentRequests() {
        assertEquals(
            ControlProtocol.Request.ListDocuments,
            ControlProtocol.parse(token, "$token\tdocuments.list"),
        )
        assertEquals(
            ControlProtocol.Request.ReadDocument("a"),
            ControlProtocol.parse(token, "$token\tdocuments.read\ta"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Document.Create("a")),
            ControlProtocol.parse(token, "$token\tdocuments.create\ta"),
        )
        assertEquals(
            ControlProtocol.Request.Execute(
                Command.Document.Save("a", "text\twith\ttabs\nand newlines", "rev"),
            ),
            ControlProtocol.parse(
                token,
                "$token\tdocuments.save\ta\trev\ttext\twith\ttabs\nand newlines",
            ),
        )
        assertEquals(
            ControlProtocol.Request.Execute(Command.Document.Delete("a", "rev")),
            ControlProtocol.parse(token, "$token\tdocuments.delete\ta\trev"),
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
        assertNull(ControlProtocol.parse(token, "$token\tdocuments.save\ta\trev"))
        assertNull(ControlProtocol.parse(token, "$token\tdocuments.delete\ta"))
    }
}
