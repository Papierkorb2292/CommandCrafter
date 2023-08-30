package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection

class ServerBreakpoint<Location>(
    val unparsed: UnparsedServerBreakpoint,
    val debuggerConnection: EditorDebugConnection,
    var action: BreakpointAction<Location>? = null
)