package net.papierkorb2292.command_crafter.editor.debugger.server

import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.DebugHandlerFactory
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.Breakpoint

class ServerDebugManager(private val server: MinecraftServer) {
    companion object {
        private val additionalDebugHandlers = mutableMapOf<PackContentFileType, DebugHandlerFactory>()
    }

    val functionDebugHandler = FunctionDebugHandler(server)

    private val debugHandlers = additionalDebugHandlers.mapValues { (_, factory) ->
        factory.createDebugHandler(server)
    } + (PackContentFileType.FunctionsFileType to functionDebugHandler)

    fun setBreakpoints(
        breakpoints: Array<UnparsedServerBreakpoint>,
        fileType: PackContentFileType,
        id: Identifier,
        player: ServerPlayerEntity,
        debuggerConnector: EditorDebugConnection
    ): Array<Breakpoint> {

        val debugHandler = debugHandlers[fileType]
            ?: return MinecraftDebuggerServer.rejectAllBreakpoints(
                breakpoints,
                MinecraftDebuggerServer.FILE_TYPE_NOT_SUPPORTED_REJECTION_REASON
            )
        return debugHandler.setBreakpoints(breakpoints, id, player, debuggerConnector)
    }

    fun removePlayer(player: ServerPlayerEntity) {
        debugHandlers.values.forEach { it.removePlayer(player) }
    }
}