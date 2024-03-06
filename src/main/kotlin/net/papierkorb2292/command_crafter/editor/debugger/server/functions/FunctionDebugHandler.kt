package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import it.unimi.dsi.fastutil.objects.Reference2IntMap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser.Companion.parseBreakpointsAndRejectRest
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandlerFactory
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.removeExtension
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.DebugHandler
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.*
import java.util.*

class FunctionDebugHandler(private val server: MinecraftServer) : DebugHandler {
    companion object {
        fun getSourceName(function: Identifier, sourceReference: Int? = INITIAL_SOURCE_REFERENCE) =
            getSourceName(function.toString(), sourceReference)

        fun getSourceName(base: String, sourceReference: Int? = INITIAL_SOURCE_REFERENCE): String {
            return if(sourceReference != INITIAL_SOURCE_REFERENCE) {
                "$base@$sourceReference"
            } else base
        }

        private val FUNCTION_FILE_EXTENSTION = ".mcfunction"
    }

    private val breakpointManager = BreakpointManager(::parseBreakpoints)

    private fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
        fileId: Identifier,
        fileSourceReference: Int?,
    ): List<Breakpoint> {
        val functionId = fileId.removeExtension(FUNCTION_FILE_EXTENSTION) ?: return MinecraftDebuggerServer.rejectAllBreakpoints(
            breakpoints,
            MinecraftDebuggerServer.UNKNOWN_FUNCTION_REJECTION_REASON
        )
        val source = Source().apply {
            this.name = getSourceName(functionId, fileSourceReference)
            this.path = PackContentFileType.FunctionsFileType.toStringPath(functionId)
        }
        val optionalFunction = server.commandFunctionManager.getFunction(functionId)
        return optionalFunction.map { function ->
            @Suppress("UNCHECKED_CAST")
            val debugInformation = (function as DebugInformationContainer<FunctionBreakpointLocation, FunctionDebugFrame>).`command_crafter$getDebugInformation`()
                ?: return@map MinecraftDebuggerServer.rejectAllBreakpoints(
                    breakpoints,
                    MinecraftDebuggerServer.DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON,
                    source
                )
            val parsedBreakpoints = debugInformation.parseBreakpointsAndRejectRest(breakpoints, server ,fileSourceReference)
            for(parsedBreakpoint in parsedBreakpoints) {
                parsedBreakpoint.source = source
            }
            parsedBreakpoints
        }.orElseGet {
            MinecraftDebuggerServer.rejectAllBreakpoints(
                breakpoints,
                MinecraftDebuggerServer.UNKNOWN_FUNCTION_REJECTION_REASON,
                source
            )
        }
    }

    override fun setBreakpoints(
        sourceBreakpoints: Array<UnparsedServerBreakpoint>,
        id: Identifier,
        player: ServerPlayerEntity,
        debuggerConnection: ServerNetworkDebugConnection,
        sourceReference: Int?
    ): Array<Breakpoint> = breakpointManager.onBreakpointUpdate(
        sourceBreakpoints,
        debuggerConnection,
        player,
        id,
        sourceReference
    ).toTypedArray()

    override fun removePlayer(player: ServerPlayerEntity) {
        breakpointManager.removePlayer(player)
    }

    fun functionHasBreakpoints(id: Identifier) = breakpointManager.breakpoints.values.any { it.containsKey(id) }

    fun getFunctionBreakpoints(id: Identifier, sourceReferences: Reference2IntMap<ServerPlayNetworkHandler>? = null) =
        breakpointManager.breakpoints.entries.flatMap { (networkHandler, functionBreakpoints) ->
            @Suppress("DEPRECATION")
            functionBreakpoints[id]?.get(sourceReferences?.get(networkHandler)) ?: emptyList()
        }

    fun reloadBreakpoints() {
        for(playerBreakpoints in breakpointManager.breakpoints.values) {
            for((sourceFile, sourceReferences) in playerBreakpoints) {
                for((sourceReference, serverBreakpoints) in sourceReferences) {
                    for(breakpoint in serverBreakpoints) {
                        breakpoint.action = null
                    }
                    val optionalFunction =
                        server.commandFunctionManager.getFunction(sourceFile.removeExtension(FUNCTION_FILE_EXTENSTION))
                    val validatedBreakpoints = optionalFunction.map { function ->
                        @Suppress("UNCHECKED_CAST")
                        val debugInformation =
                            (function as DebugInformationContainer<FunctionBreakpointLocation, FunctionDebugFrame>).`command_crafter$getDebugInformation`()
                                ?: return@map MinecraftDebuggerServer.rejectAllBreakpoints(
                                    serverBreakpoints,
                                    MinecraftDebuggerServer.DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON
                                )
                        debugInformation.parseBreakpointsAndRejectRest(LinkedList(serverBreakpoints), server, sourceReference)
                    }.orElseGet {
                        MinecraftDebuggerServer.rejectAllBreakpoints(
                            serverBreakpoints,
                            MinecraftDebuggerServer.UNKNOWN_FUNCTION_REJECTION_REASON
                        )
                    }
                    validatedBreakpoints.forEachIndexed { index, breakpoint ->
                        serverBreakpoints[index].editorConnection.updateReloadedBreakpoint(BreakpointEventArguments().apply {
                            this.breakpoint = breakpoint
                            this.reason = BreakpointEventArgumentsReason.CHANGED
                        })
                    }
                }
            }
        }
    }

    fun addNewSourceReferenceBreakpoints(
        breakpoints: List<SourceBreakpoint>,
        debuggerConnection: ServerNetworkDebugConnection,
        sourceFile: Identifier,
        sourceReference: Int?,
    ) {
        breakpointManager.addNewSourceReferenceBreakpoints(
            breakpoints,
            debuggerConnection,
            sourceFile.withExtension(FUNCTION_FILE_EXTENSTION),
            sourceReference
        )
    }
}

typealias FunctionDebugInformation = DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame>
typealias FunctionDebugPauseHandlerFactory = DebugPauseHandlerFactory<@JvmWildcard FunctionDebugFrame>