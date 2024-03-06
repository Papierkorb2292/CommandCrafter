package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection

class ServerBreakpoint<Location>(
    val unparsed: UnparsedServerBreakpoint,
    val editorConnection: ServerNetworkDebugConnection,
    var action: BreakpointAction<Location>? = null
) {
    fun copyUnparsed(): ServerBreakpoint<Location> {
        return ServerBreakpoint(unparsed, editorConnection)
    }
}