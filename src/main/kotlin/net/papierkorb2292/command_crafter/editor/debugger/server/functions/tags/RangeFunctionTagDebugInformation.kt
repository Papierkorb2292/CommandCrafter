package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.mojang.brigadier.context.StringRange
import net.minecraft.server.MinecraftServer
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebuggerVisualContext
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.helper.getDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.getFileBreakpointRange
import net.papierkorb2292.command_crafter.editor.debugger.server.StepInTargetsManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointAction
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResult
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResultValueReference
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ServerCommandSourceValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.StringMapValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.createScope
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.helper.arrayOfNotNull
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class RangeFunctionTagDebugInformation(
    private val sourceFileEntries: List<TagEntriesRangeFile>,
    private val conditionParser: BreakpointConditionParser?
) : FunctionTagDebugInformation {
    companion object {
        private const val COMMAND_SOURCE_SCOPE_NAME = "Command-Source"
        private const val FUNCTION_TAG_MACROS_SCOPE_NAME = "Macros"
        private const val FUNCTION_TAG_RESULT_SCOPE_NAME = "Command-Result"
    }

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

    override fun createDebugPauseHandler(debugFrame: FunctionTagDebugFrame) = object : DebugPauseHandler {

        override fun findNextPauseLocation() {
            debugFrame.pauseOnEntryIndex(debugFrame.currentEntryIndex + 1)
        }

        override fun getStackFrames(): List<MinecraftStackFrame> {
            val sourceReference = debugFrame.currentSourceReference
            val source = Source().apply {
                name = FunctionTagDebugHandler.getSourceName(debugFrame.tagId.toString(), sourceReference)
                path = debugFrame.filePath
            }

            if(sourceReference == INITIAL_SOURCE_REFERENCE) {
                // Return dummy frame until source reference is created
                return listOf(
                    MinecraftStackFrame(
                    "", DebuggerVisualContext(source, Range()), emptyArray()
                )
                )
            }

            val variablesReferenceMapper = debugFrame.pauseContext.variablesReferenceMapper
            val serverCommandSourceScope = ServerCommandSourceValueReference(
                variablesReferenceMapper,
                debugFrame.commandSource
            ).createScope(COMMAND_SOURCE_SCOPE_NAME)

            val lastFunctionResult = FunctionDebugFrame.commandResult.getOrNull() ?: CommandResult(null)
            val commandResultScope = TagResultValueReference(
                variablesReferenceMapper,
                CommandResultValueReference(variablesReferenceMapper, lastFunctionResult) { lastFunctionResult},
                CommandResultValueReference(variablesReferenceMapper, CommandResult(debugFrame.accumulatedResult)) {
                    if(it.returnValue != null) {
                        debugFrame.setAccumulatedResult(it.returnValue.first, it.returnValue.second)
                        it
                    } else CommandResult(debugFrame.accumulatedResult)
                }
            ).createScope(FUNCTION_TAG_RESULT_SCOPE_NAME)

            val macrosScope =
                if(debugFrame.macroArguments.isNotEmpty())
                    StringMapValueReference(
                        debugFrame.pauseContext.variablesReferenceMapper,
                        debugFrame.macroNames.zip(debugFrame.macroArguments).toMap()
                    ).createScope(FUNCTION_TAG_MACROS_SCOPE_NAME)
                else
                    null

            val variableScopes = arrayOfNotNull(serverCommandSourceScope, commandResultScope, macrosScope)

            val hasRunThrough = debugFrame.sourceReferenceEntries!!.size <= debugFrame.currentEntryIndex
            val fileRange = debugFrame.sourceReferenceFileRange!!

            val result = mutableListOf(
                MinecraftStackFrame(
                    '#' + debugFrame.tagId.toString(),
                    DebuggerVisualContext(source, fileRange),
                    variableScopes
                )
            )

            result += if(hasRunThrough) {
                MinecraftStackFrame(
                    "return",
                    DebuggerVisualContext(source, Range(fileRange.end.advance(-1), fileRange.end)),
                    variableScopes
                )
            } else {
                val currentEntry = debugFrame.sourceReferenceEntries!![debugFrame.currentEntryIndex]
                val currentEntryId = currentEntry.first
                val currentEntryRange = currentEntry.second

                MinecraftStackFrame(
                    currentEntryId.toString(),
                    DebuggerVisualContext(source, currentEntryRange),
                    variableScopes
                )
            }

            return result
        }

        override fun onExitFrame() { }

        override fun next(granularity: SteppingGranularity) {
            debugFrame.pauseOnEntryIndex(debugFrame.currentEntryIndex + 1)
        }

        override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
            debugFrame.pauseContext.stepIntoFrame()
        }

        override fun stepOut(granularity: SteppingGranularity) {
            debugFrame.pauseContext.pauseAfterExitFrame()
        }

        override fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse> =
            CompletableFuture.completedFuture(StepInTargetsResponse().apply {
                targets = arrayOf(StepInTarget().apply {
                    id = debugFrame.pauseContext.stepInTargetsManager.addStepInTarget(StepInTargetsManager.Target {
                        debugFrame.pauseContext.stepIntoFrame()
                    })
                    val currentFunctionId = debugFrame.pauseContext.server.commandFunctionManager
                        .getTag(debugFrame.tagId)!!
                        .elementAt(debugFrame.currentEntryIndex)
                    label = "Step into function '$currentFunctionId'"
                })
            })

        override fun continue_() {
            debugFrame.pauseContext.removePause()
        }
    }
}