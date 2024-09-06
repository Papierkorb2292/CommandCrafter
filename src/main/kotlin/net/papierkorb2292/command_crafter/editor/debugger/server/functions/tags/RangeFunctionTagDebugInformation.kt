package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.mojang.brigadier.context.StringRange
import net.minecraft.registry.tag.TagEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.getFileBreakpointRange
import net.papierkorb2292.command_crafter.editor.debugger.server.StepInTargetsManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointAction
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.*
import net.papierkorb2292.command_crafter.editor.debugger.variables.StringMapValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.createScope
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.helper.arrayOfNotNull
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.InlineTagFunctionIdContainer
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class RangeFunctionTagDebugInformation(
    private val tagEntrySources: List<TagEntrySource>,
    private val sourceFileEntries: List<TagEntriesRangeFile>,
    private val conditionParser: BreakpointConditionParser?
) : FunctionTagDebugInformation {
    companion object {
        private const val COMMAND_SOURCE_SCOPE_NAME = "Command-Source"
        private const val FUNCTION_TAG_MACROS_SCOPE_NAME = "Macros"
        private const val FUNCTION_TAG_RESULT_SCOPE_NAME = "Command-Result"

        fun getFullFileIdFromFinalEntry(finalEntry: TagFinalEntriesValueGetter.FinalEntry): PackagedId {
            val packPath = PackagedId.getPackIdWithoutPrefix(finalEntry.trackedEntry.source)
            @Suppress("CAST_NEVER_SUCCEEDS")
            val inlineTagFunctionPath = (finalEntry.trackedEntry as InlineTagFunctionIdContainer).`command_crafter$getInlineTagFunctionId`()
            val identifier = if(inlineTagFunctionPath == null) {
                Identifier.of(
                    finalEntry.sourceId.namespace,
                    "${PackContentFileType.FUNCTION_TAGS_FILE_TYPE.contentTypePath}/${finalEntry.sourceId.path}${FunctionTagDebugHandler.TAG_FILE_EXTENSION}"
                )
            } else {
                Identifier.of(
                    inlineTagFunctionPath.namespace,
                    "${PackContentFileType.FUNCTIONS_FILE_TYPE.contentTypePath}/${inlineTagFunctionPath.path}${FunctionDebugHandler.FUNCTION_FILE_EXTENSTION}"
                )
            }
            return PackagedId(identifier, packPath)
        }

        fun fromFinalTagContentProvider(finalTags: FinalTagContentProvider): Map<Identifier, RangeFunctionTagDebugInformation> {
            val fileContent = finalTags.`command_crafter$getFileContent`()
            val finalEntries = finalTags.`command_crafter$getFinalTags`()

            val tagEntriesRangeFiles = mutableMapOf<Identifier, MutableList<TagEntriesRangeFile>>()

            for((id, entries) in finalEntries.entries) {
                for((entryIndex, entry) in entries.map { it.getLastChild() }.withIndex()) {
                    val tagEntriesRangeFilesForSource =
                        tagEntriesRangeFiles.getOrPut(entry.sourceId, ::mutableListOf)
                    val packIdWithoutPrefix = PackagedId.getPackIdWithoutPrefix(entry.trackedEntry.source)
                    var tagEntriesRangeFile = tagEntriesRangeFilesForSource.find { it.packId == packIdWithoutPrefix }
                    if(tagEntriesRangeFile == null) {
                        val fileId = getFullFileIdFromFinalEntry(entry)
                        val content = fileContent[fileId]
                        if(content == null) {
                            CommandCrafter.LOGGER.error("Could not find tag file content for id ${fileId}, available ids: ${fileContent.keys.joinToString(", ")}")
                            continue
                        }
                        tagEntriesRangeFile = TagEntriesRangeFile(
                            packIdWithoutPrefix,
                            FileMappingInfo(content),
                            mutableListOf()
                        )
                        tagEntriesRangeFilesForSource += tagEntriesRangeFile
                    }
                    val entryRange = (entry.trackedEntry.entry as StringRangeContainer).`command_crafter$getRange`()!!
                    val fileEntryIndex = tagEntriesRangeFile.entries.binarySearch { it.range.compareTo(entryRange) }
                    if(fileEntryIndex < 0) {
                        tagEntriesRangeFile.entries.add(-fileEntryIndex - 1, FileEntry(entryRange, FunctionTagBreakpointLocation(mutableMapOf(id to entryIndex))))
                        continue
                    }
                    tagEntriesRangeFile.entries[fileEntryIndex].breakpointLocation.entryIndexPerTag[id] = entryIndex
                }
            }

            return finalEntries.mapValues { (id, entries) ->
                RangeFunctionTagDebugInformation(
                    entries.map {
                        val pathToEntry = mutableListOf<TagEntrySourcePathSegment>()
                        var entry: TagFinalEntriesValueGetter.FinalEntry? = it
                        while(entry != null) {
                            pathToEntry += TagEntrySourcePathSegment(
                                getFullFileIdFromFinalEntry(entry),
                                FileMappingInfo(fileContent[getFullFileIdFromFinalEntry(entry)]!!),
                                entry.trackedEntry.entry
                            )
                            entry = entry.child
                        }
                        TagEntrySource(pathToEntry)
                    },
                    tagEntriesRangeFiles[id] ?: emptyList(),
                    null
                )
            }
        }
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

        setBreakpoints(addedBreakpoints, debugConnection, sourceFile.fileId, server)

        return result
    }

    private fun setBreakpoints(
        addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionTagBreakpointLocation>,
        debugConnection: EditorDebugConnection,
        fileId: PackagedId,
        server: MinecraftServer,
    ) {
        val tagDebugHandler = server.getDebugManager().functionTagDebugHandler
        tagDebugHandler.updateGroupKeyBreakpoints(
            fileId.resourceId,
            INITIAL_SOURCE_REFERENCE,
            debugConnection,
            BreakpointManager.BreakpointGroupKey(this, fileId),
            addedBreakpoints,
            null
        )
    }

    private fun getEntriesForFile(file: BreakpointManager.FileBreakpointSource): TagEntriesRangeFile? {
        val pack = file.fileId.packPath
        if(sourceFileEntries.isEmpty())
            return null
        if(sourceFileEntries.size == 1)
            return sourceFileEntries.first()
        return sourceFileEntries.find { pack.endsWith(it.packId) }
    }

    data class TagEntriesRangeFile(
        val packId: String,
        val mappingInfo: FileMappingInfo,
        val entries: MutableList<FileEntry>
    )

    data class FileEntry(
        val range: StringRange,
        val breakpointLocation: FunctionTagBreakpointLocation
    )

    override fun createDebugPauseHandler(debugFrame: FunctionTagDebugFrame) = object : DebugPauseHandler {

        override fun findNextPauseLocation() {
            if(tagEntrySources.isEmpty()) {
                // Don't pause, because there would be no location for the "return" stack frame
                debugFrame.pauseContext.debugConnection!!.output(OutputEventArguments().apply {
                    category = OutputEventArgumentsCategory.IMPORTANT
                    output = "Tag has no entries"
                })
                debugFrame.pauseContext.pauseAfterExitFrame()
                return
            }
            debugFrame.pauseOnEntryIndex(debugFrame.currentEntryIndex + 1)
        }

        override fun getStackFrames(): List<MinecraftStackFrame> {
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

            val hasRunThrough = tagEntrySources.size <= debugFrame.currentEntryIndex

            if(hasRunThrough) {
                val lastEntryPathStart = tagEntrySources.last().pathToEntry.first()
                val lastEntryEnd = (lastEntryPathStart.tagEntry as StringRangeContainer).`command_crafter$getRange`()!!.end
                return listOf(MinecraftStackFrame(
                    "return",
                    DebuggerVisualContext(
                        Source().apply {
                            name = FunctionTagDebugHandler.getSourceName(lastEntryPathStart.fileId)
                            path = "**/${lastEntryPathStart.fileId.packPath}/data/${lastEntryPathStart.fileId.resourceId.namespace}/${lastEntryPathStart.fileId.resourceId.path}"
                        },
                        Range(
                            AnalyzingResult.getPositionFromCursor(lastEntryEnd, lastEntryPathStart.fileMappingInfo, false),
                            AnalyzingResult.getPositionFromCursor(lastEntryEnd + 1, lastEntryPathStart.fileMappingInfo, false)
                        )
                    ),
                    variableScopes
                ))
            }
            val result = mutableListOf<MinecraftStackFrame>()
            val currentEntry = tagEntrySources[debugFrame.currentEntryIndex]

            for(pathSegment in currentEntry.pathToEntry) {
                val segmentStringRange = (pathSegment.tagEntry as StringRangeContainer).`command_crafter$getRange`()!!
                result += MinecraftStackFrame(
                    pathSegment.tagEntry.toString(),
                    DebuggerVisualContext(
                        Source().apply {
                            name = FunctionTagDebugHandler.getSourceName(pathSegment.fileId)
                            path = "**/${pathSegment.fileId.packPath}/data/${pathSegment.fileId.resourceId.namespace}/${pathSegment.fileId.resourceId.path}"
                        },
                        Range(
                            AnalyzingResult.getPositionFromCursor(segmentStringRange.start, pathSegment.fileMappingInfo, false),
                            AnalyzingResult.getPositionFromCursor(segmentStringRange.end, pathSegment.fileMappingInfo, false)
                        )
                    ),
                    variableScopes
                )
            }

            return result
        }

        override fun onExitFrame() { }
        override fun onHandlerSectionEnter() { }
        override fun onHandlerSectionExit() { }

        override fun next(granularity: SteppingGranularity) {
            if(debugFrame.currentEntryIndex >= tagEntrySources.size) {
                debugFrame.pauseContext.pauseAfterExitFrame()
                return
            }
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

    data class TagEntrySource(
        val pathToEntry: List<TagEntrySourcePathSegment>
    )

    data class TagEntrySourcePathSegment(
        val fileId: PackagedId,
        val fileMappingInfo: FileMappingInfo,
        val tagEntry: TagEntry,
    )
}