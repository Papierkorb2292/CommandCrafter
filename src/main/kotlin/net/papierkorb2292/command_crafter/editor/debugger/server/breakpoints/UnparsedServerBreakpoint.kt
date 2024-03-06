package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import org.eclipse.lsp4j.debug.SourceBreakpoint

class UnparsedServerBreakpoint(
    val id: Int,
    val sourceReference: Int?,
    val sourceBreakpoint: SourceBreakpoint
)