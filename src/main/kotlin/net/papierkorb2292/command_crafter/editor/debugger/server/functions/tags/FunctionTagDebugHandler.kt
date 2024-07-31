package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.google.gson.JsonElement
import com.mojang.brigadier.context.StringRange
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.function.FunctionLoader
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser.Companion.parseBreakpointsAndRejectRest
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.IdentifiedDebugInformationProvider
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.DebugHandler
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.mixin.MinecraftServerAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ResourceManagerHolderAccessor
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.Source
import java.util.*

class FunctionTagDebugHandler(private val server: MinecraftServer) : DebugHandler {
    companion object {
        val TAG_PATH = RegistryKeys.getTagPath(FunctionLoader.FUNCTION_REGISTRY_KEY)
        val TAG_PARSING_ELEMENT_RANGES = ThreadLocal<Map<JsonElement, StringRange>>()
        val TAG_FILE_EXTENSION = ".json"

        fun getSourceName(function: PackagedId, sourceReference: Int? = ServerDebugManager.INITIAL_SOURCE_REFERENCE) =
            getSourceName(function.toString(), sourceReference)

        fun getSourceName(base: String, sourceReference: Int? = ServerDebugManager.INITIAL_SOURCE_REFERENCE): String {
            return if(sourceReference != ServerDebugManager.INITIAL_SOURCE_REFERENCE) {
                "fun #$base@$sourceReference"
            } else "fun #$base"
        }
    }

    private val breakpointManager = BreakpointManager(::parseBreakpoints, server)

    fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<FunctionTagBreakpointLocation>>,
        file: BreakpointManager.FileBreakpointSource,
        debugConnection: EditorDebugConnection,
    ): List<Breakpoint> {
        val source = Source().apply {
            this.name = getSourceName(file.fileId, file.sourceReference)
            this.path = PackContentFileType.FUNCTION_TAGS_FILE_TYPE.toStringPath(file.fileId.withExtension(TAG_FILE_EXTENSION))
            this.sourceReference = file.sourceReference
        }
        @Suppress("CAST_NEVER_SUCCEEDS")
        val functionLoader = ((server as MinecraftServerAccessor).resourceManagerHolder as ResourceManagerHolderAccessor).dataPackContents.functionLoader
        @Suppress("UNCHECKED_CAST")
        val breakpointParser = (functionLoader as IdentifiedDebugInformationProvider<FunctionTagBreakpointLocation, *>).`command_crafter$getDebugInformation`(file.fileId.resourceId)
            ?: return MinecraftDebuggerServer.rejectAllBreakpoints(
                breakpoints,
                MinecraftDebuggerServer.DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON,
                source
            )
        val parsedBreakpoints = breakpointParser.parseBreakpointsAndRejectRest(breakpoints, server, file, debugConnection)
        for(parsedBreakpoint in parsedBreakpoints) {
            parsedBreakpoint.source = source
        }
        return parsedBreakpoints
    }

    override fun setBreakpoints(
        sourceBreakpoints: Array<UnparsedServerBreakpoint>,
        id: PackagedId,
        player: ServerPlayerEntity,
        debugConnection: EditorDebugConnection,
        sourceReference: Int?,
    ): Array<Breakpoint> = breakpointManager.onBreakpointUpdate(
        sourceBreakpoints,
        debugConnection,
        id.removeExtension(TAG_FILE_EXTENSION) ?: id,
        sourceReference
    ).toTypedArray()

    override fun removeDebugConnection(debugConnection: EditorDebugConnection) {
        breakpointManager.removeDebugConnection(debugConnection)
    }

    override fun removeSourceReference(debugConnection: EditorDebugConnection, sourceReference: Int) {
        breakpointManager.removeSourceReference(debugConnection, sourceReference)
    }

    override fun onReload() {
        breakpointManager.reloadBreakpoints()
    }

    fun getTagBreakpoints(id: Identifier): List<ServerBreakpoint<FunctionTagBreakpointLocation>> =
        breakpointManager.breakpoints.entries.flatMap { (_, functionBreakpoints) ->
            functionBreakpoints.values
        }.flatMap {
            it[INITIAL_SOURCE_REFERENCE]?.values ?: emptyList()
        }.flatMap {
            it.list
        }.filter {
            val action = it.action ?: return@filter false
            action.location.entryIndexPerTag.containsKey(id)
        }

    fun updateGroupKeyBreakpoints(
        functionId: Identifier,
        sourceReference: Int?,
        debugConnection: EditorDebugConnection,
        breakpointGroupKey: BreakpointManager.BreakpointGroupKey<FunctionTagBreakpointLocation>,
        addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionTagBreakpointLocation>,
        sourceReferenceMapping: BreakpointManager.SourceReferenceMappingSupplier?,
    ) {
        breakpointManager.setGroupBreakpoints(
            functionId,
            sourceReference,
            breakpointGroupKey,
            addedBreakpoints,
            debugConnection,
            sourceReferenceMapping
        )
    }
}

typealias FunctionTagDebugInformation = DebugInformation<FunctionTagBreakpointLocation, FunctionTagDebugFrame>