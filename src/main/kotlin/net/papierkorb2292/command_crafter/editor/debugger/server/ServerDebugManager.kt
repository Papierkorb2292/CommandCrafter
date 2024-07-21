package net.papierkorb2292.command_crafter.editor.debugger.server

import com.mojang.brigadier.context.StringRange
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.DebugHandlerFactory
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugHandler
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugHandler
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.SourceResponse

class ServerDebugManager(private val server: MinecraftServer) {
    companion object {
        val INITIAL_SOURCE_REFERENCE: Int? = null

        private val additionalDebugHandlers = mutableMapOf<PackContentFileType, DebugHandlerFactory>()

        // TODO: Use FileMappingInfo instead of raw lines
        fun <TBreakpointLocation> getFileBreakpointRange(breakpoint: ServerBreakpoint<TBreakpointLocation>, lines: List<String>): StringRange {
            val sourceBreakpoint = breakpoint.unparsed.sourceBreakpoint
            val column = sourceBreakpoint.column
            return if (column == null) {
                AnalyzingResult.getLineCursorRange(sourceBreakpoint.line, lines)
            } else {
                val breakpointCursor = AnalyzingResult.getCursorFromPosition(
                    lines,
                    Position(sourceBreakpoint.line, column),
                    false
                )
                StringRange.at(breakpointCursor)
            }
        }
    }

    val functionDebugHandler = FunctionDebugHandler(server)
    val functionTagDebugHandler = FunctionTagDebugHandler(server)

    private val debugHandlers = mapOf(
        PackContentFileType.FUNCTIONS_FILE_TYPE to functionDebugHandler,
        PackContentFileType.FUNCTION_TAGS_FILE_TYPE to functionTagDebugHandler
    ) + additionalDebugHandlers.mapValues { (_, factory) ->
        factory.createDebugHandler(server)
    }

    private val sourceReferencesMap = mutableMapOf<EditorDebugConnection, PlayerSourceReferences>()

    fun setBreakpoints(
        breakpoints: Array<UnparsedServerBreakpoint>,
        fileType: PackContentFileType,
        id: PackagedId,
        player: ServerPlayerEntity,
        debuggerConnector: ServerNetworkDebugConnection,
        sourceReference: Int? = null
    ): Array<Breakpoint> {

        val debugHandler = debugHandlers[fileType]
            ?: return MinecraftDebuggerServer.rejectAllBreakpoints(
                breakpoints,
                MinecraftDebuggerServer.FILE_TYPE_NOT_SUPPORTED_REJECTION_REASON
            )
        return debugHandler.setBreakpoints(breakpoints, id, player, debuggerConnector, sourceReference)
    }

    fun removeDebugConnection(debugConnection: EditorDebugConnection) {
        debugHandlers.values.forEach { it.removeDebugConnection(debugConnection) }
        sourceReferencesMap.remove(debugConnection)
    }

    fun onReload() {
        debugHandlers.values.forEach { it.onReload() }
    }

    fun removeSourceReference(debugConnection: EditorDebugConnection, sourceReference: Int) {
        val playerReferences = sourceReferencesMap[debugConnection] ?: return
        playerReferences.sources.remove(sourceReference)
        for(debugHandler in debugHandlers.values)
            debugHandler.removeSourceReference(debugConnection, sourceReference)
        if(playerReferences.sources.isEmpty()) sourceReferencesMap.remove(debugConnection)
    }

    fun addSourceReference(debugConnection: EditorDebugConnection, originalLines: List<String>, response: SourceReferenceSupplier): Int {
        val references = sourceReferencesMap.getOrPut(debugConnection, ::PlayerSourceReferences)
        val id = debugConnection.nextSourceReference
        debugConnection.onSourceReferenceAdded()
        references.sources[id] = SourceReferenceGenerator(response, originalLines)
        return id
    }

    fun getSourceReferenceEntry(debugConnection: EditorDebugConnection, sourceReference: Int?): SourceReferenceGenerator? {
        val sourceReferences = sourceReferencesMap[debugConnection] ?: return null
        return sourceReferences.sources[sourceReference ?: return null]
    }

    fun getSourceReferenceLines(debugConnection: EditorDebugConnection, sourceReference: Int?): List<String>? {
        val sourceReferences = sourceReferencesMap[debugConnection] ?: return null
        val sourceReferenceId = sourceReference ?: return null
        return sourceReferences.sources[sourceReferenceId]?.getLinesOrGenerate(sourceReferenceId)
    }

    fun retrieveSourceReference(debugConnection: EditorDebugConnection, sourceReference: Int): SourceResponse? {
        val sourceReferences = sourceReferencesMap[debugConnection] ?: return null
        return sourceReferences.sources[sourceReference]?.generateSourceReference(sourceReference)
    }

    class PlayerSourceReferences(val sources: Int2ReferenceMap<SourceReferenceGenerator> = Int2ReferenceOpenHashMap())

    class SourceReferenceGenerator(private val responseCallback: SourceReferenceSupplier, val originalLines: List<String>) {
        var generatedResponse: SourceResponse? = null
            private set
        val generatedLines: List<String>?
            get() = generatedResponse?.content?.lines()

        fun generateSourceReference(id: Int) =
            generatedResponse ?: responseCallback(id).also { response ->
                generatedResponse = response
            }

        fun getLinesOrGenerate(id: Int) = generatedLines ?: generateSourceReference(id).content.lines()
    }
}

typealias SourceReferenceSupplier = (Int) -> SourceResponse