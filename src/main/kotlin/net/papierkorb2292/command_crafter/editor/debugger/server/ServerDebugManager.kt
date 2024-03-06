package net.papierkorb2292.command_crafter.editor.debugger.server

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
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
    } + (PackContentFileType.FunctionsFileType to functionDebugHandler)

    private val sourceReferencesMap = mutableMapOf<ServerPlayNetworkHandler, PlayerSourceReferences>()

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

    fun removePlayer(player: ServerPlayerEntity) {
        debugHandlers.values.forEach { it.removePlayer(player) }
        sourceReferencesMap.remove(player.networkHandler)
    }

    fun removeSourceReference(networkHandler: ServerPlayNetworkHandler, sourceReference: Int) {
        val playerReferences = sourceReferencesMap[networkHandler] ?: return
        playerReferences.sources.remove(sourceReference)
        if(playerReferences.sources.isEmpty()) sourceReferencesMap.remove(networkHandler)
    }

    fun addSourceReference(player: ServerPlayerEntity, response: (Int) -> SourceResponse): Int {
        val references = sourceReferencesMap.getOrPut(player.networkHandler, ::PlayerSourceReferences)
        val id = references.nextId++
        references.sources[id] = SourceReferenceGenerator(response)
        return id
    }

    fun getSourceReferenceLines(player: ServerPlayerEntity, sourceReference: Int?): List<String>? {
        val sourceReferences = sourceReferencesMap[player.networkHandler] ?: return null
        return sourceReference?.let { sourceReferences.sources[it]?.generatedLines }
    }

    fun retrieveSourceReference(player: ServerPlayerEntity, sourceReference: Int): SourceResponse? {
        val sourceReferences = sourceReferencesMap[player.networkHandler] ?: return null
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