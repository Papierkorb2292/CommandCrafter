package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import org.eclipse.lsp4j.debug.Breakpoint

interface DebugHandler {
    fun setBreakpoints(
        sourceBreakpoints: Array<UnparsedServerBreakpoint>,
        id: PackagedId,
        debugConnection: EditorDebugConnection,
        sourceReference: Int? = null
    ): Array<Breakpoint>
    fun removeDebugConnection(debugConnection: EditorDebugConnection)
    fun removeSourceReference(debugConnection: EditorDebugConnection, sourceReference: Int)
    fun onReload()
}