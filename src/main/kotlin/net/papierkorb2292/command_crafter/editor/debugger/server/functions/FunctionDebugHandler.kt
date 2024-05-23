package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import it.unimi.dsi.fastutil.objects.Reference2IntMap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser.Companion.parseBreakpointsAndRejectRest
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandlerFactory
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.removeExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.DebugHandler
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.Source
import java.util.*

class FunctionDebugHandler(private val server: MinecraftServer) : DebugHandler {
    companion object {
        fun getSourceName(function: Identifier, sourceReference: Int? = INITIAL_SOURCE_REFERENCE) =
            getSourceName(function.toString(), sourceReference)

        fun getSourceName(base: String, sourceReference: Int? = INITIAL_SOURCE_REFERENCE): String {
            return if(sourceReference != INITIAL_SOURCE_REFERENCE) {
                "fun $base@$sourceReference"
            } else "fun $base"
        }

        private val FUNCTION_FILE_EXTENSTION = ".mcfunction"
    }

    private val breakpointManager = BreakpointManager(::parseBreakpoints, server)

    private fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
        functionId: Identifier,
        fileSourceReference: Int?,
        debugConnection: EditorDebugConnection
    ): List<Breakpoint> {
        val source = Source().apply {
            this.name = getSourceName(functionId, fileSourceReference)
            this.path = PackContentFileType.FUNCTIONS_FILE_TYPE.toStringPath(functionId)
            this.sourceReference = fileSourceReference
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
            val parsedBreakpoints = debugInformation.parseBreakpointsAndRejectRest(breakpoints, server, fileSourceReference, debugConnection)
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
        debugConnection: EditorDebugConnection,
        sourceReference: Int?
    ): Array<Breakpoint> = breakpointManager.onBreakpointUpdate(
        sourceBreakpoints,
        debugConnection,
        id.removeExtension(FUNCTION_FILE_EXTENSTION) ?: id,
        sourceReference
    ).toTypedArray()

    override fun removeDebugConnection(debugConnection: EditorDebugConnection) {
        breakpointManager.removeDebugConnection(debugConnection)
    }

    override fun removeSourceReference(debugConnection: EditorDebugConnection, sourceReference: Int?) {
        breakpointManager.removeSourceReference(debugConnection, sourceReference)
    }

    fun functionHasBreakpoints(id: Identifier) = breakpointManager.breakpoints.values.any { it.containsKey(id) }

    fun getFunctionBreakpoints(id: Identifier, sourceReferences: Reference2IntMap<EditorDebugConnection>? = null): List<ServerBreakpoint<FunctionBreakpointLocation>> =
        breakpointManager.breakpoints.entries.flatMap { (debugConnection, functionBreakpoints) ->
            @Suppress("DEPRECATION")
            functionBreakpoints[id]?.get(sourceReferences?.get(debugConnection))?.values ?: emptyList()
        }.flatMap { it.list }

    override fun onReload() {
        breakpointManager.reloadBreakpoints()
    }

    fun addNewSourceReferenceBreakpoints(
        breakpoints: List<BreakpointManager.NewSourceReferenceBreakpoint>,
        debuggerConnection: EditorDebugConnection,
        resourceId: Identifier,
        sourceReference: Int?,
    ) {
        breakpointManager.addNewSourceReferenceBreakpoints(
            breakpoints,
            debuggerConnection,
            resourceId,
            sourceReference
        )
    }

    fun updateBreakpointParserBreakpoints(functionId: Identifier, sourceReference: Int?, debugConnection: EditorDebugConnection, breakpointParser: BreakpointParser<FunctionBreakpointLocation>, breakpointList: BreakpointManager.AddedBreakpointList<FunctionBreakpointLocation>) {
        breakpointManager.setParserBreakpoints(
            functionId,
            sourceReference,
            breakpointParser,
            breakpointList,
            debugConnection
        )
    }
}

typealias FunctionDebugInformation = DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame>
typealias FunctionDebugPauseHandlerFactory = DebugPauseHandlerFactory<@JvmWildcard FunctionDebugFrame>