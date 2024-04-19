package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.papierkorb2292.command_crafter.editor.debugger.helper.copy
import org.eclipse.lsp4j.debug.SourceBreakpoint

data class UnparsedServerBreakpoint(
    val id: Int,
    val sourceReference: Int?,
    val sourceBreakpoint: SourceBreakpoint,
    val originBreakpointId: Int? = null
) {
    fun copy() = UnparsedServerBreakpoint(
        id,
        sourceReference,
        sourceBreakpoint.copy(),
        originBreakpointId
    )
}