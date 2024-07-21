package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.mojang.brigadier.context.StringRange
import net.minecraft.server.MinecraftServer
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.getDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.getFileBreakpointRange
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointAction
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.debug.Breakpoint
import java.util.*

class RangeFunctionTagBreakpointParser(
    private val sourceFileEntries: List<TagEntriesRangeFile>,
    private val conditionParser: BreakpointConditionParser?
) : FunctionTagBreakpointParser {
    override fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<FunctionTagBreakpointLocation>>,
        server: MinecraftServer,
        sourceFile: BreakpointManager.FileBreakpointSource,
        debugConnection: EditorDebugConnection,
    ): List<Breakpoint> {
        val file = getEntriesForFile(sourceFile) ?: return emptyList()

        val result = mutableListOf<Breakpoint>()
        val addedBreakpoints = BreakpointManager.AddedBreakpointList<FunctionTagBreakpointLocation>()
        val entries = file.entries
        var nextEntryIndex = 0
        breakpoints@while(breakpoints.isNotEmpty()) {
            val breakpoint = breakpoints.peek()
            val breakpointRange = getFileBreakpointRange(breakpoint, file.mappingInfo.lines)
            // Skip all entries that are before this breakpoint
            while(breakpointRange > entries[nextEntryIndex].range) {
                if(++nextEntryIndex >= file.entries.size) {
                    break@breakpoints
                }
            }
            val entry = entries[nextEntryIndex]
            if(breakpointRange < entry.range) {
                // The breakpoint is before the entry and no previous entry contained it
                breakpoints.poll()
                result += MinecraftDebuggerServer.rejectBreakpoint(
                    breakpoint.unparsed,
                    MinecraftDebuggerServer.BREAKPOINT_AT_NO_CODE_REJECTION_REASON
                )
                addedBreakpoints.list += breakpoint
                continue
            }
            // The entry contains the breakpoint
            breakpoints.poll()
            breakpoint.action = BreakpointAction(
                entry.breakpointLocation,
                conditionParser?.parseCondition(
                    breakpoint.unparsed.sourceBreakpoint.condition,
                    breakpoint.unparsed.sourceBreakpoint.hitCondition
                )
            )
            addedBreakpoints.list += breakpoint
            result += MinecraftDebuggerServer.acceptBreakpoint(breakpoint.unparsed)
        }

        if(sourceFile.sourceReference == INITIAL_SOURCE_REFERENCE) {
            setInitialSourceReferenceBreakpoints(addedBreakpoints, debugConnection, sourceFile.fileId, server)
        } else {
            setSourceReferenceBreakpoints(addedBreakpoints, debugConnection, sourceFile, server)
        }

        return result
    }

    private fun setInitialSourceReferenceBreakpoints(
        addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionTagBreakpointLocation>,
        debugConnection: EditorDebugConnection,
        fileId: PackagedId,
        server: MinecraftServer,
    ) {
        val tagDebugHandler = server.getDebugManager().functionTagDebugHandler
        addedBreakpoints.list.asSequence()
            .flatMap { it.action?.location?.entryIndexPerTag?.keys ?: emptyList() }
            .distinct()
            .forEach {
                tagDebugHandler.updateGroupKeyBreakpoints(
                    it,
                    INITIAL_SOURCE_REFERENCE,
                    debugConnection,
                    BreakpointManager.BreakpointGroupKey(this, fileId),
                    addedBreakpoints,
                    null //TODO
                )
            }
    }

    private fun setSourceReferenceBreakpoints(
        addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionTagBreakpointLocation>,
        debugConnection: EditorDebugConnection,
        sourceFile: BreakpointManager.FileBreakpointSource,
        server: MinecraftServer,
    ) {
        server.getDebugManager().functionTagDebugHandler.updateGroupKeyBreakpoints(
            sourceFile.fileId.resourceId,
            sourceFile.sourceReference,
            debugConnection,
            BreakpointManager.BreakpointGroupKey(this, sourceFile.fileId),
            addedBreakpoints,
            null
        )
    }

    private fun getEntriesForFile(file: BreakpointManager.FileBreakpointSource): TagEntriesRangeFile? {
        val pack = file.fileId.packPath
        val sourceReference = file.sourceReference
        if(sourceReference != INITIAL_SOURCE_REFERENCE) {
            require(pack.isEmpty()) { "Encountered source reference for a tag with a pack path: $file" }
            TODO("Not yet implemented: Get all entries and map their position to the source reference")
        }
        if(sourceFileEntries.isEmpty())
            return null
        if(sourceFileEntries.size == 1)
            return sourceFileEntries.first()
        if(sourceFileEntries.size == 2 && pack != "vanilla") {
            val nonVanilla = sourceFileEntries.filter { it.packId != "vanilla" }
            if(nonVanilla.size == 1)
                return nonVanilla.first()
        }
        return sourceFileEntries.find { it.packId == pack }
    }

    data class TagEntriesRangeFile(
        val packId: String,
        val mappingInfo: FileMappingInfo,
        val entries: List<FileEntry>
    )

    data class FileEntry(
        val range: StringRange,
        val breakpointLocation: FunctionTagBreakpointLocation
    )
}