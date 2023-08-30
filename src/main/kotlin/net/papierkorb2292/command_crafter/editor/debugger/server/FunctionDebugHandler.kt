package net.papierkorb2292.command_crafter.editor.debugger.server

import com.mojang.brigadier.ParseResults
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser.Companion.parseBreakpointsAndRejectRest
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandlerFactory
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.removeExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.*
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.*
import org.eclipse.lsp4j.debug.Breakpoint
import java.util.*

class FunctionDebugHandler(private val server: MinecraftServer) : DebugHandler {
    companion object {
        val currentPauseContext = ThreadLocal<FunctionPauseContextImpl?>()
        val currentDispatcherContext = ThreadLocal<DispatcherContext?>()

        private val FUNCTION_FILE_EXTENSTION = ".mcfunction"
    }

    private val breakpointMap = BreakpointMap<FunctionBreakpointLocation>()

    override fun setBreakpoints(
        sourceBreakpoints: Array<UnparsedServerBreakpoint>,
        id: Identifier,
        player: ServerPlayerEntity,
        debuggerConnection: EditorDebugConnection
    ): Array<Breakpoint> {
        if(sourceBreakpoints.isEmpty()) {
            breakpointMap.removeSourceFile(player, id)
            return emptyArray()
        }

        val (serverBreakpointsQueue, unsortIndices) = breakpointMap.setBreakpointsAndSort(
            player,
            id,
            sourceBreakpoints,
            debuggerConnection
        )

        val optionalFunction = server.commandFunctionManager.getFunction(id.removeExtension(FUNCTION_FILE_EXTENSTION))
        return optionalFunction.map { function ->
            @Suppress("UNCHECKED_CAST")
            val debugInformation = (function as DebugInformationContainer<FunctionBreakpointLocation, FunctionPauseContext>).`command_crafter$getDebugInformation`()
                ?: return@map MinecraftDebuggerServer.rejectAllBreakpoints(
                    sourceBreakpoints,
                    MinecraftDebuggerServer.DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON
                )
            val validatedBreakpoints = debugInformation.parseBreakpointsAndRejectRest(serverBreakpointsQueue, server)
            Array(validatedBreakpoints.size) {
                validatedBreakpoints[unsortIndices[it]]
            }
        }.orElseGet {
            MinecraftDebuggerServer.rejectAllBreakpoints(
                sourceBreakpoints,
                MinecraftDebuggerServer.UNKNOWN_FUNCTION_REJECTION_REASON
            )
        }
    }

    override fun removePlayer(player: ServerPlayerEntity) {
        breakpointMap.removePlayer(player)
    }

    fun getBreakpoints(parseResults: ParseResults<ServerCommandSource>)
        = breakpointMap.getFilteredBreakpoints {
            it.commandLocationRoot == parseResults.context
        }

    fun reloadBreakpoints() {
        for(playerBreakpoints in breakpointMap.breakpoints.values) {
            for((sourceFile, serverBreakpoints) in playerBreakpoints) {
                for(breakpoint in serverBreakpoints) {
                    breakpoint.action = null
                }
                val optionalFunction = server.commandFunctionManager.getFunction(sourceFile.removeExtension(FUNCTION_FILE_EXTENSTION))
                val validatedBreakpoints = optionalFunction.map { function ->
                    @Suppress("UNCHECKED_CAST")
                    val debugInformation = (function as DebugInformationContainer<FunctionBreakpointLocation, FunctionPauseContext>).`command_crafter$getDebugInformation`()
                        ?: return@map MinecraftDebuggerServer.rejectAllBreakpoints(
                            serverBreakpoints,
                            MinecraftDebuggerServer.DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON
                        )
                    debugInformation.parseBreakpointsAndRejectRest(LinkedList(serverBreakpoints), server)
                }.orElseGet {
                    MinecraftDebuggerServer.rejectAllBreakpoints(
                        serverBreakpoints,
                        MinecraftDebuggerServer.UNKNOWN_FUNCTION_REJECTION_REASON
                    )
                }
                validatedBreakpoints.forEachIndexed { index, breakpoint ->
                    serverBreakpoints[index].debuggerConnection.updateReloadedBreakpoint(breakpoint)
                }
            }
        }
    }
}

typealias FunctionDebugInformation = DebugInformation<FunctionBreakpointLocation, FunctionPauseContext>
typealias FunctionDebugPauseHandlerCreator = DebugPauseHandlerFactory<@JvmWildcard FunctionPauseContext>