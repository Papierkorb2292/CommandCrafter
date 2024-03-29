package net.papierkorb2292.command_crafter.editor.debugger.server

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.DebugHandlerFactory
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugHandler
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.SourceResponse

class ServerDebugManager(private val server: MinecraftServer) {
    companion object {
        val INITIAL_SOURCE_REFERENCE: Int? = null

        private val additionalDebugHandlers = mutableMapOf<PackContentFileType, DebugHandlerFactory>()
    }

    val functionDebugHandler = FunctionDebugHandler(server)

    private val debugHandlers = additionalDebugHandlers.mapValues { (_, factory) ->
        factory.createDebugHandler(server)
    } + (PackContentFileType.FUNCTIONS_FILE_TYPE to functionDebugHandler)

    private val sourceReferencesMap = mutableMapOf<EditorDebugConnection, PlayerSourceReferences>()

    fun setBreakpoints(
        breakpoints: Array<UnparsedServerBreakpoint>,
        fileType: PackContentFileType,
        id: Identifier,
        player: ServerPlayerEntity,
        debuggerConnector: ServerNetworkDebugConnection
    ): Array<Breakpoint> {

        val debugHandler = debugHandlers[fileType]
            ?: return MinecraftDebuggerServer.rejectAllBreakpoints(
                breakpoints,
                MinecraftDebuggerServer.FILE_TYPE_NOT_SUPPORTED_REJECTION_REASON
            )
        return debugHandler.setBreakpoints(breakpoints, id, player, debuggerConnector)
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
        if(playerReferences.sources.isEmpty()) sourceReferencesMap.remove(debugConnection)
    }

    fun addSourceReference(debugConnection: EditorDebugConnection, response: (Int) -> SourceResponse): Int {
        val references = sourceReferencesMap.getOrPut(debugConnection, ::PlayerSourceReferences)
        val id = references.nextId++
        references.sources[id] = SourceReferenceGenerator(response)
        return id
    }

    fun getSourceReferenceLines(debugConnection: EditorDebugConnection, sourceReference: Int?): List<String>? {
        val sourceReferences = sourceReferencesMap[debugConnection] ?: return null
        return sourceReference?.let { sourceReferences.sources[it]?.generatedLines }
    }

    fun retrieveSourceReference(debugConnection: EditorDebugConnection, sourceReference: Int): SourceResponse? {
        val sourceReferences = sourceReferencesMap[debugConnection] ?: return null
        return sourceReferences.sources[sourceReference]?.generateSourceReference(sourceReference)
    }

    class PlayerSourceReferences(val sources: Int2ReferenceMap<SourceReferenceGenerator> = Int2ReferenceOpenHashMap(), var nextId: Int = 0)

    class SourceReferenceGenerator(private val responseCallback: (Int) -> SourceResponse) {
        var generatedLines: List<String> = emptyList()
            private set

        fun generateSourceReference(id: Int) =
            responseCallback(id).also { response ->
                generatedLines = response.content.split('\n')
            }
    }
}