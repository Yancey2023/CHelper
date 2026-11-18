package yancey.chelper.desktop.window

import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.domain.library.LibraryFunction

class DesktopLoongFlowCloseTest {
    @Test
    fun closingTheApplicationRequiresExportConfirmationAndRestoresThePanel() {
        val host = DesktopLoongFlow({}, { null }, {})
        host.openExport()
        val model = requireNotNull(host.model)
        model.currentBlockInput = "say preserved"
        model.saveCurrentBlock()
        host.minimized = true
        var exits = 0
        assertTrue(host.requestApplicationExit { exits++ })
        assertEquals(0, exits)
        assertFalse(host.minimized)
        assertEquals(1L, host.closeRequest)
        assertEquals("say preserved", host.model?.collectedBlocks?.single()?.command)
        host.cancelClose()
        host.dismiss()
        assertEquals(
            "A later manual close must not execute the cancelled application exit",
            0,
            exits,
        )
    }

    @Test
    fun confirmedExportCloseContinuesTheApplicationExitExactlyOnce() {
        val host = DesktopLoongFlow({}, { null }, {})
        host.openExport()
        var exits = 0
        assertTrue(host.requestApplicationExit { exits++ })
        host.dismiss()
        host.dismiss()
        assertEquals(1, exits)
        assertNull(host.model)
    }

    @Test
    fun anImportSessionDoesNotBlockApplicationExit() {
        val host = DesktopLoongFlow({}, { null }, {})
        assertFalse(host.requestApplicationExit { error("No export to confirm") })
        host.openImport(LibraryFunction(content = "say one"))
        assertFalse(host.requestApplicationExit { error("No export to confirm") })
    }
}
