package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import org.eclipse.lsp4j.debug.Breakpoint

interface DebugHandler {
    fun setBreakpoints(sourceBreakpoints: Array<UnparsedServerBreakpoint>, id: Identifier, player: ServerPlayerEntity, debuggerConnection: EditorDebugConnection): Array<Breakpoint>
    fun removePlayer(player: ServerPlayerEntity)
}