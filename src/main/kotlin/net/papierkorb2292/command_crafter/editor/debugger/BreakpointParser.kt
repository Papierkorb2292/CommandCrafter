package net.papierkorb2292.command_crafter.editor.debugger

import net.minecraft.server.MinecraftServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import org.eclipse.lsp4j.debug.Breakpoint
import java.util.*

interface BreakpointParser<TBreakpointLocation> {
    companion object {
        fun <TBreakpointLocation> BreakpointParser<TBreakpointLocation>.parseBreakpointsAndRejectRest(breakpoints: Queue<ServerBreakpoint<TBreakpointLocation>>, server: MinecraftServer, sourceFile: BreakpointManager.FileBreakpointSource, debugConnection: EditorDebugConnection): List<Breakpoint> {
            return parseBreakpoints(breakpoints, server, sourceFile, debugConnection) + Array(breakpoints.size) {
                MinecraftDebuggerServer.rejectBreakpoint(
                    breakpoints.poll().unparsed,
                    MinecraftDebuggerServer.BREAKPOINT_AT_NO_CODE_REJECTION_REASON
                )
            }
        }
    }

    fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<TBreakpointLocation>>,
        server: MinecraftServer,
        sourceFile: BreakpointManager.FileBreakpointSource,
        debugConnection: EditorDebugConnection,
    ): List<Breakpoint>
}