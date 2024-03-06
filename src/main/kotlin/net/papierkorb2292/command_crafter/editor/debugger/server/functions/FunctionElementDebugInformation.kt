package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.ParsedArgument
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.command.SingleCommandAction
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.Macro
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.server.FileContentReplacer
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.StepInTargetsManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ArgumentBreakpointParserSupplier
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointAction
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ContextChainAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.SingleCommandActionAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.VariableLineAccessor
import net.papierkorb2292.command_crafter.parser.helper.MacroCursorMapperProvider
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class FunctionElementDebugInformation(
    private val elements: List<FunctionElementProcessor>,
    private val lines: List<String>,
    private val conditionParser: BreakpointConditionParser,
    private val sourceFunctionFile: Identifier
) : FunctionDebugInformation {
    companion object {
        private const val COMMAND_SOURCE_SCOPE_NAME = "Command-Source"
        private const val STEP_IN_NEXT_SECTION_BEGINNING_LABEL = "Next section: beginning"
        private const val STEP_IN_NEXT_SECTION_CURRENT_SOURCE_LABEL = "Next section: follow context"
        private const val STEP_IN_NEXT_SECTION_NEXT_SOURCE_LABEL = "Next section: follow next context"
        private const val STEP_IN_CURRENT_SECTION_LABEL = "Current section: "

        fun getFileBreakpointRange(breakpoint: ServerBreakpoint<FunctionBreakpointLocation>, lines: List<String>): StringRange {
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

    private var functionStackFrameRange = Range(Position(), Position())
    private val dynamicBreakpoints = mutableMapOf<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>()

    private val staticElementPositions = mutableMapOf<CommandContext<ServerCommandSource>, MutableMap<Int?, ElementPosition>>()
    init {
        val staticCursorOffsets = mutableMapOf<CommandContext<ServerCommandSource>, ElementPosition>()
        for(element in elements) {
            element.addStaticCursorOffsets(this@FunctionElementDebugInformation, staticCursorOffsets)
        }
        staticElementPositions.clear()
        for((context, position) in staticCursorOffsets) {
            staticElementPositions[context] = mutableMapOf(INITIAL_SOURCE_REFERENCE to position)
        }
    }

    private val sourceReferenceDebugHandlers = mutableMapOf<Int, FunctionElementDebugPauseHandler>()

    fun setFunctionStringRange(stringRange: StringRange) {
        functionStackFrameRange = Range(
            AnalyzingResult.getPositionFromCursor(stringRange.start, lines, false),
            AnalyzingResult.getPositionFromCursor(stringRange.end, lines, false)
        )
    }

    var functionId: Identifier? = null

    override fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
        server: MinecraftServer,
        sourceReference: Int?
    ): List<Breakpoint> {
        val result: MutableList<Breakpoint> = ArrayList()
        dynamicBreakpoints.clear()
        for(element in elements) {
            if(breakpoints.isEmpty()) break
            element.parseBreakpoints(breakpoints, server, this, result, dynamicBreakpoints, sourceReference)
        }
        return result
    }

    override fun createDebugPauseHandler(debugFrame: FunctionDebugFrame) = FunctionElementDebugPauseHandler(debugFrame)

    inner class FunctionElementDebugPauseHandler(val debugFrame: FunctionDebugFrame) : DebugPauseHandler, FileContentReplacer {
        val dynamicElementPositions = mutableMapOf<CommandContext<ServerCommandSource>, MutableMap<Int?, ElementPosition>>()

        private val sourceReferences = mutableSetOf<Int>()

        private var stepInTargetSourceIndex: Int? = null
        private var stepInTargetSourceSection: Int? = null

        private val onReloadBreakpoints = {
            if(dynamicBreakpoints.isNotEmpty()) {
                val serverBreakpoints = debugFrame.breakpoints.toMutableList()
                for((element, breakpoints) in dynamicBreakpoints) {
                    val copiedBreakpoints = breakpoints.map { it.copyUnparsed() }

                    element.parseDynamicBreakpoints(
                        copiedBreakpoints,
                        this@FunctionElementDebugInformation,
                        debugFrame
                    )

                    serverBreakpoints += copiedBreakpoints
                }
                debugFrame.breakpoints = serverBreakpoints
            }
        }

        init {
            debugFrame.pauseContext.addOnContinueListener(onReloadBreakpoints)
            onReloadBreakpoints()
            
            val dynamicCursorOffsets = mutableMapOf<CommandContext<ServerCommandSource>, ElementPosition>()
            for(element in elements) {
                element.addDynamicCursorOffsets(this@FunctionElementDebugInformation, dynamicCursorOffsets, debugFrame)
            }
            dynamicElementPositions.clear()
            for((context, position) in dynamicCursorOffsets) {
                dynamicElementPositions[context] = mutableMapOf(INITIAL_SOURCE_REFERENCE to position)
            }
        }

        private fun getCursorOffset(rootContext: CommandContext<ServerCommandSource>, sourceReference: Int?): Int? {
            val sourceReferenceCursorPositions = staticElementPositions[rootContext] ?: dynamicElementPositions[rootContext] ?: return null
            return sourceReferenceCursorPositions[sourceReference]?.cursor
        }

        override fun next(granularity: SteppingGranularity) {
            if(debugFrame.currentSectionSources.hasNext()) {
                stepInTargetSourceSection = debugFrame.currentSectionIndex
                stepInTargetSourceIndex = debugFrame.currentSectionSources.currentSourceIndex + 1
                debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, debugFrame.currentSectionIndex)
                return
            }
            pauseAtNextCommand()
        }
        override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
            if(debugFrame.hasNextSection()) {
                debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, debugFrame.currentSectionIndex + 1)
                return
            }
            next(granularity)
        }

        override fun shouldStopOnCurrentContext(): Boolean {
            stepInTargetSourceIndex?.let { targetSourceIndex ->
                var sourceIndex = debugFrame.currentSectionSources.currentSourceIndex
                stepInTargetSourceSection?.let {
                    for(i in debugFrame.currentSectionIndex downTo it + 1) {
                        sourceIndex = debugFrame.sectionSources[i].parentSourceIndices[sourceIndex]
                    }
                }
                if(sourceIndex == targetSourceIndex) {
                    stepInTargetSourceIndex = null
                    stepInTargetSourceSection = null
                    return true
                }
                if(sourceIndex > targetSourceIndex) {
                    stepInTargetSourceIndex = null
                    stepInTargetSourceSection = null
                    debugFrame.pauseContext.notifyClientPauseLocationSkipped()
                    return true
                }
                return false
            }
            return true
        }

        override fun stepOut(granularity: SteppingGranularity) {
            if(debugFrame.currentSectionIndex == 0) {
                debugFrame.pauseContext.pauseAfterExitFrame()
                return
            }
            pauseAtNextCommand()
        }

        override fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse> {
            fun buildStepInCurrentLabel(section: CommandContext<*>): String {
                return STEP_IN_CURRENT_SECTION_LABEL +
                        section.nodes.asSequence().take(2).fold("") { acc, parsedCommandNode ->
                            val node = parsedCommandNode.node
                            if (node is LiteralCommandNode<*>)
                                acc + " " + node.literal
                            else
                                acc
                        }
            }

            if(frameId == 0) {
                //frameId is the function frame, which doesn't have any step in targets
                return CompletableFuture.completedFuture(StepInTargetsResponse())
            }
            val sectionIndex = frameId - 1
            @Suppress("UNCHECKED_CAST")
            val modifiers = (debugFrame.currentContextChain as ContextChainAccessor<ServerCommandSource>).modifiers
            if(sectionIndex > modifiers.size) {
                //frameId refers to no section
                return CompletableFuture.completedFuture(StepInTargetsResponse())
            }

            val targets = mutableListOf<StepInTarget>()
            val targetsManager = debugFrame.pauseContext.stepInTargetsManager
            if(sectionIndex == modifiers.size) {
                val executable = (debugFrame.currentContextChain as ContextChainAccessor<*>).executable
                if((executable.command as? PotentialDebugFrameInitiator)?.`command_crafter$willInitiateDebugFrame`() == true) {
                    targets += StepInTarget().also {
                        it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                            debugFrame.pauseContext.stepIntoFrame()
                        })
                        it.label = buildStepInCurrentLabel(executable)
                    }
                }
            } else {
                val modifier = modifiers[sectionIndex]
                if(sectionIndex == debugFrame.currentSectionIndex) {
                    targets += StepInTarget().also {
                        it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                            debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, sectionIndex)
                        })
                        it.label = STEP_IN_NEXT_SECTION_BEGINNING_LABEL
                    }
                    val nextSectionSources = debugFrame.sectionSources[sectionIndex + 1]
                    val currentSourceIndex = debugFrame.currentSectionSources.currentSourceIndex
                    targets += StepInTarget().also {
                        it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                            stepInTargetSourceIndex = currentSourceIndex
                            stepInTargetSourceSection = sectionIndex
                            debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, sectionIndex)
                        })
                        it.label = STEP_IN_NEXT_SECTION_CURRENT_SOURCE_LABEL
                    }
                    if((modifier.redirectModifier as? PotentialDebugFrameInitiator)?.`command_crafter$willInitiateDebugFrame`() == true
                        && (nextSectionSources.parentSourceIndices.isEmpty()
                                || nextSectionSources.parentSourceIndices.last() < currentSourceIndex)) {

                        targets += StepInTarget().also {
                            it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                                debugFrame.pauseContext.stepIntoFrame()
                            })
                            it.label = buildStepInCurrentLabel(modifier)
                        }
                    }
                } else {
                    var ancestorSourceIndex = debugFrame.currentSectionSources.currentSourceIndex
                    for(i in debugFrame.currentSectionIndex downTo sectionIndex + 1 ) {
                        ancestorSourceIndex = debugFrame.sectionSources[i].parentSourceIndices[ancestorSourceIndex]
                    }
                    if(debugFrame.sectionSources[sectionIndex].sources.size > ancestorSourceIndex + 1) {
                        targets += StepInTarget().also {
                            it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                                stepInTargetSourceIndex = ancestorSourceIndex + 1
                                stepInTargetSourceSection = sectionIndex
                                debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, debugFrame.currentSectionIndex)
                            })
                            it.label = STEP_IN_NEXT_SECTION_NEXT_SOURCE_LABEL
                        }
                    }
                }
            }
            return CompletableFuture.completedFuture(StepInTargetsResponse().apply {
                this.targets = targets.toTypedArray()
            })
        }

        override fun continue_() {
            debugFrame.pauseContext.removePause()
        }

        override fun findNextPauseLocation() {
            debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, debugFrame.currentSectionIndex)
        }

        fun pauseAtNextCommand() {
            if (debugFrame.currentCommandIndex + 1 >= debugFrame.contextChains.size) {
                debugFrame.pauseContext.pauseAfterExitFrame()
                return
            }
            debugFrame.pauseAtSection(debugFrame.contextChains[debugFrame.currentCommandIndex + 1].topContext, 0)
        }

        override fun getStackFrames(sourceReference: Int?): List<MinecraftStackFrame> {
            val contextChain = debugFrame.currentContextChain
            val cursorOffset = getCursorOffset(contextChain.topContext, sourceReference) ?: return emptyList()

            fun createServerCommandSourceScope(source: ServerCommandSource, setter: ((ServerCommandSource) -> Unit)? = null): Scope {
                val variablesReferencer = ServerCommandSourceValueReference(debugFrame.pauseContext.variablesReferenceMapper, source, setter)
                return Scope().apply {
                    name = COMMAND_SOURCE_SCOPE_NAME
                    variablesReference = debugFrame.pauseContext.variablesReferenceMapper.addVariablesReferencer(variablesReferencer)
                    namedVariables = variablesReferencer.namedVariableCount
                    indexedVariables = variablesReferencer.indexedVariableCount
                }
            }

            val source = Source().apply {
                name = FunctionDebugHandler.getSourceName(sourceFunctionFile)
                path = PackContentFileType.FunctionsFileType.toStringPath(sourceFunctionFile)
            }

            val stackFrames = ArrayList<MinecraftStackFrame>(debugFrame.currentSectionIndex + 2)
            stackFrames += MinecraftStackFrame(
                functionId.toString(),
                DebuggerVisualContext(
                    source,
                    functionStackFrameRange
                ),
                arrayOf(
                    createServerCommandSourceScope(debugFrame.currentSource)
                    //TODO: Macros
                )
            )

            fun addStackFrameForSection(context: CommandContext<*>, sectionIndex: Int, sourceIndex: Int) {
                val stringRange = context.range
                val firstParsedNode = context.nodes.firstOrNull()
                stackFrames.add(1, MinecraftStackFrame(
                    firstParsedNode?.node?.toString() ?: "<null>",
                    DebuggerVisualContext(
                        source,
                        Range(
                            AnalyzingResult.getPositionFromCursor(stringRange.start + cursorOffset, lines, false),
                            AnalyzingResult.getPositionFromCursor(stringRange.end + cursorOffset, lines, false)
                        )
                    ),
                    arrayOf(
                        if (sectionIndex == debugFrame.currentSectionIndex)
                            createServerCommandSourceScope(debugFrame.currentSource) {
                                debugFrame.currentSource = it
                            }
                        else
                            createServerCommandSourceScope(debugFrame.sectionSources[sectionIndex].sources[sourceIndex])
                    )
                ))
            }

            @Suppress("UNCHECKED_CAST")
            val modifiers = (contextChain as ContextChainAccessor<ServerCommandSource>).modifiers
            var lastRunningModifier = debugFrame.currentSectionIndex
            var sourceIndex = debugFrame.currentSectionSources.currentSourceIndex

            if(debugFrame.currentSectionIndex == modifiers.size) {
                @Suppress("UNCHECKED_CAST")
                addStackFrameForSection((contextChain as ContextChainAccessor<ServerCommandSource>).executable, debugFrame.currentSectionIndex, sourceIndex)
                lastRunningModifier -= 1
                sourceIndex = debugFrame.sectionSources[debugFrame.currentSectionIndex].parentSourceIndices[sourceIndex]
            }

            for(i in lastRunningModifier downTo 0) {
                addStackFrameForSection(modifiers[i], i, sourceIndex)
                sourceIndex = debugFrame.sectionSources[debugFrame.currentSectionIndex].parentSourceIndices[sourceIndex]
            }

            return stackFrames
        }

        override fun onExitFrame() {
            debugFrame.pauseContext.removeOnContinueListener(onReloadBreakpoints)
            sourceReferenceDebugHandlers -= sourceReferences
            for(sourceReferenceMap in staticElementPositions.values)
                sourceReferenceMap -= sourceReferences
        }

        override fun getReplacementData(path: String): FileContentReplacer.ReplacementDataProvider {
            fun addSourceReference(sourceReference: Int) {
                sourceReferences += sourceReference
                sourceReferenceDebugHandlers[sourceReference] = this
            }

            val replacements = elements.asSequence().mapNotNull { it.getReplacings(path, debugFrame, this@FunctionElementDebugInformation)?.asSequence() }.flatten()
            if(!replacements.iterator().hasNext())
                return FileContentReplacer.ReplacementDataProvider(emptySequence(), emptySequence(), ::addSourceReference)
            val staticPositionables = staticElementPositions.mapValues {
                (_, sourceReferenceMap) -> sourceReferenceMap[INITIAL_SOURCE_REFERENCE]!!.copy()
            }
            val dynamicPositionables = dynamicElementPositions.mapValues {
                (_, sourceReferenceMap) -> sourceReferenceMap[INITIAL_SOURCE_REFERENCE]!!.copy()
            }
            return FileContentReplacer.ReplacementDataProvider(
                replacements,
                staticPositionables.values.asSequence() + dynamicPositionables.values.asSequence()
            ) { sourceReference ->
                addSourceReference(sourceReference)
                staticPositionables.forEach {
                    (context, elementPosition) -> staticElementPositions[context]?.put(sourceReference, elementPosition)
                }
                dynamicPositionables.forEach {
                    (context, elementPosition) -> dynamicElementPositions[context]?.put(sourceReference, elementPosition)
                }
            }
        }
    }

    interface FunctionElementProcessor {
        fun parseBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            debugInformation: FunctionElementDebugInformation,
            parsed: MutableList<Breakpoint>,
            dynamics: MutableMap<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>,
            sourceReference: Int?,
        )
        fun parseDynamicBreakpoints(
            breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>,
            debugInformation: FunctionElementDebugInformation,
            frame: FunctionDebugFrame
        )

        fun addStaticCursorOffsets(
            debugInformation: FunctionElementDebugInformation,
            map: MutableMap<CommandContext<ServerCommandSource>, ElementPosition>,
        )
        fun addDynamicCursorOffsets(
            debugInformation: FunctionElementDebugInformation,
            map: MutableMap<CommandContext<ServerCommandSource>, ElementPosition>,
            frame: FunctionDebugFrame
        )

        fun getReplacings(
            path: String,
            frame: FunctionDebugFrame,
            debugInformation: FunctionElementDebugInformation,
        ): Iterator<FileContentReplacer.Replacing>?
    }

    class CommandContextElementProcessor(val rootContext: CommandContext<ServerCommandSource>, val cursorOffset: Int, val breakpointRangeGetter: ((ServerBreakpoint<FunctionBreakpointLocation>) -> StringRange)? = null) : FunctionElementProcessor {
        override fun parseBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            debugInformation: FunctionElementDebugInformation,
            parsed: MutableList<Breakpoint>,
            dynamics: MutableMap<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>,
            sourceReference: Int?
        ) {
            val functionId = debugInformation.functionId ?: return
            val cursorOffset =
                if(sourceReference == INITIAL_SOURCE_REFERENCE)
                    cursorOffset
                else
                    debugInformation.staticElementPositions[rootContext]?.get(sourceReference)?.cursor ?: return
            val functionFileRange = StringRange(
                rootContext.range.start + cursorOffset,
                rootContext.lastChild.range.end + cursorOffset
            )
            breakpoints@while(breakpoints.isNotEmpty()) {
                val breakpoint = breakpoints.peek()
                val breakpointRange = breakpointRangeGetter?.invoke(breakpoint) ?: getFileBreakpointRange(breakpoint, debugInformation.lines)
                val comparedToCurrentElement =
                    breakpointRange.compareTo(functionFileRange)
                if(comparedToCurrentElement > 0) {
                    // The breakpoint is after the element
                    break
                }
                if(comparedToCurrentElement == 0) {
                    // The element contains the breakpoint
                    var context: CommandContext<ServerCommandSource>? = this.rootContext
                    val relativeBreakpointCursor = breakpointRange - cursorOffset
                    // Find the context containing the breakpoint
                    contexts@while(context != null) {
                        if((context.redirectModifier as? ForkableNoPauseFlag)?.`command_crafter$cantPause`() == true) {
                            context = context.child
                            continue
                        }
                        for(parsedNode in context.nodes) {
                            val comparedToNode = relativeBreakpointCursor.compareTo(parsedNode.range)
                            if(comparedToNode <= 0) {
                                if(comparedToNode < 0) {
                                    // The node is after the breakpoint
                                    // (meaning no previous node contained the breakpoint)
                                    break@contexts
                                }
                                //The node contains the breakpoint
                                val node = parsedNode.node
                                if(node is ArgumentCommandNode<*, *>) {
                                    val type = node.type
                                    val argument = context.getArgument(node.name, ParsedArgument::class.java)
                                    if(type is ArgumentBreakpointParserSupplier && argument != null) {
                                        val breakpointList =
                                            type.`command_crafter$getBreakpointParser`(argument.result, server)?.parseBreakpoints(
                                                breakpoints, server, null
                                            )
                                        if (!breakpointList.isNullOrEmpty()) {
                                            parsed += breakpointList
                                            continue@breakpoints
                                        }
                                    }
                                }
                                breakpoints.poll()
                                breakpoint.action = BreakpointAction(
                                    FunctionBreakpointLocation(functionId, context, rootContext),
                                    debugInformation.conditionParser.parseCondition(
                                        breakpoint.unparsed.sourceBreakpoint.condition,
                                        breakpoint.unparsed.sourceBreakpoint.hitCondition
                                    )
                                )
                                parsed += MinecraftDebuggerServer.acceptBreakpoint(breakpoint.unparsed)
                                continue@breakpoints
                            }
                        }
                        context = context.child
                    }
                }
                // The element is after the breakpoint (meaning no previous element
                // contained the breakpoint) or the current element contained the
                // breakpoint, but no nodes of the breakpoint contained it
                breakpoints.poll()
                parsed += MinecraftDebuggerServer.rejectBreakpoint(
                    breakpoint.unparsed,
                    MinecraftDebuggerServer.BREAKPOINT_AT_NO_CODE_REJECTION_REASON
                )
            }
        }
        override fun parseDynamicBreakpoints(
            breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>,
            debugInformation: FunctionElementDebugInformation,
            frame: FunctionDebugFrame
        ) { }

        override fun addStaticCursorOffsets(debugInformation: FunctionElementDebugInformation, map: MutableMap<CommandContext<ServerCommandSource>, ElementPosition>) {
            map[rootContext] = ElementPosition.fromCursor(cursorOffset, debugInformation.lines)
        }
        override fun addDynamicCursorOffsets(debugInformation: FunctionElementDebugInformation, map: MutableMap<CommandContext<ServerCommandSource>, ElementPosition>, frame: FunctionDebugFrame) { }

        override fun getReplacings(path: String, frame: FunctionDebugFrame, debugInformation: FunctionElementDebugInformation): Nothing? = null
    }

    class MacroElementProcessor(
        private val elementIndex: Int,
        private val macroFileRange: StringRange,
        private val macroLine: Macro.VariableLine<ServerCommandSource>,
        private val macroInFileCursorMapper: ProcessedInputCursorMapper?,
    ): FunctionElementProcessor {
        override fun parseBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            debugInformation: FunctionElementDebugInformation,
            parsed: MutableList<Breakpoint>,
            dynamics: MutableMap<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>,
            sourceReference: Int?,
        ) {
            if(sourceReference == INITIAL_SOURCE_REFERENCE) {
                parseInitialSourceBreakpoints(breakpoints, server, debugInformation, parsed, dynamics)
                return
            }
            val pauseHandler = debugInformation.sourceReferenceDebugHandlers[sourceReference] ?: return
            val frame = pauseHandler.debugFrame
            (frame.procedure.entries()[elementIndex] as? SingleCommandAction.Sourced)?.let { action ->
                @Suppress("UNCHECKED_CAST")
                val context = (action as SingleCommandActionAccessor<ServerCommandSource>).contextChain.topContext
                val cursorOffset = pauseHandler.dynamicElementPositions[context]?.get(sourceReference)?.cursor ?: return
                CommandContextElementProcessor(context, cursorOffset) {
                    val lines = (server as ServerDebugManagerContainer)
                        .`command_crafter$getServerDebugManager`()
                        .getSourceReferenceLines(it.editorConnection.player, sourceReference)
                        ?: debugInformation.lines
                    getFileBreakpointRange(it, lines)
                }.parseBreakpoints(
                    breakpoints,
                    server,
                    debugInformation,
                    parsed,
                    mutableMapOf(),
                    INITIAL_SOURCE_REFERENCE
                )
            }
        }

        private fun parseInitialSourceBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            debugInformation: FunctionElementDebugInformation,
            parsed: MutableList<Breakpoint>,
            dynamics: MutableMap<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>
        ) {
            val dynamicBreakpoints = mutableListOf<ServerBreakpoint<FunctionBreakpointLocation>>()
            breakpoints@while(breakpoints.isNotEmpty()) {
                val breakpoint = breakpoints.peek()
                val breakpointRange = getFileBreakpointRange(breakpoint, debugInformation.lines)
                val comparedToCurrentElement =
                    breakpointRange.compareTo(macroFileRange)
                if(comparedToCurrentElement <= 0) {
                    if(comparedToCurrentElement == 0) {
                        // The element contains the breakpoint
                        dynamicBreakpoints += breakpoint
                        parsed += MinecraftDebuggerServer.rejectBreakpoint(
                            breakpoint.unparsed,
                            MinecraftDebuggerServer.DYNAMIC_BREAKPOINT_REJECTION_REASON
                        )
                        continue@breakpoints
                    }
                    // The element is after the breakpoint (meaning no previous element
                    // contained the breakpoint)
                    breakpoints.poll()
                    parsed += MinecraftDebuggerServer.rejectBreakpoint(
                        breakpoint.unparsed,
                        MinecraftDebuggerServer.BREAKPOINT_AT_NO_CODE_REJECTION_REASON
                    )
                    continue@breakpoints
                }
                break
            }

            val previousSourceReferenceRemoved = dynamics[this]?.filter { it.unparsed.sourceReference != null } ?: emptyList()
            val updatedDynamicBreakpoints = previousSourceReferenceRemoved + dynamicBreakpoints
            if(updatedDynamicBreakpoints.isEmpty()) {
                dynamics.remove(this)
                return
            }
            dynamics[this] = updatedDynamicBreakpoints
        }

        override fun parseDynamicBreakpoints(
            breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>,
            debugInformation: FunctionElementDebugInformation,
            frame: FunctionDebugFrame
        ) {
            (frame.procedure.entries()[elementIndex] as? SingleCommandAction.Sourced)?.let { action ->
                val invocation = (macroLine as VariableLineAccessor).invocation
                @Suppress("CAST_NEVER_SUCCEEDS")
                val resolvedMacroCursorMapper = (invocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(frame.macroArguments)

                @Suppress("UNCHECKED_CAST")
                val context = (action as SingleCommandActionAccessor<ServerCommandSource>).contextChain.topContext
                CommandContextElementProcessor(context, 0) {
                    val breakpointFileRange = getFileBreakpointRange(it, debugInformation.lines)
                    val unresolvedMacroRange = macroInFileCursorMapper?.mapToSource(breakpointFileRange)
                        ?: StringRange(
                            breakpointFileRange.start - macroFileRange.start,
                            breakpointFileRange.end - macroFileRange.start
                        )
                    resolvedMacroCursorMapper.mapToSource(unresolvedMacroRange)
                }.parseBreakpoints(
                    LinkedList(breakpoints),
                    frame.pauseContext.server,
                    debugInformation,
                    mutableListOf(),
                    mutableMapOf(),
                    INITIAL_SOURCE_REFERENCE
                )
            }
        }

        override fun addStaticCursorOffsets(debugInformation: FunctionElementDebugInformation, map: MutableMap<CommandContext<ServerCommandSource>, ElementPosition>) { }
        override fun addDynamicCursorOffsets(
            debugInformation: FunctionElementDebugInformation,
            map: MutableMap<CommandContext<ServerCommandSource>, ElementPosition>,
            frame: FunctionDebugFrame
        ) {
            (frame.procedure.entries()[elementIndex] as? SingleCommandAction.Sourced)?.let {
                @Suppress("UNCHECKED_CAST")
                val rootContext = (it as SingleCommandActionAccessor<ServerCommandSource>).contextChain.topContext
                map[rootContext] = ElementPosition.fromCursor(macroFileRange.start, debugInformation.lines)
            }
        }

        override fun getReplacings(path: String, frame: FunctionDebugFrame, debugInformation: FunctionElementDebugInformation): Iterator<FileContentReplacer.Replacing>? {
            if(path != PackContentFileType.FunctionsFileType.toStringPath(debugInformation.sourceFunctionFile))
                return null

            fun mapToPosition(macroSourceCursor: Int): Position {
                val fileCursor = macroInFileCursorMapper?.mapToSource(macroSourceCursor) ?: (macroFileRange.start + macroSourceCursor)
                return AnalyzingResult.getPositionFromCursor(fileCursor, debugInformation.lines)
            }

            (frame.procedure.entries()[elementIndex] as? SingleCommandAction.Sourced)?.let { action ->
                val invocation = (macroLine as VariableLineAccessor).invocation
                val variableIndices = macroLine.dependentVariables
                val variableArguments = variableIndices.map { frame.macroArguments[it] }
                @Suppress("CAST_NEVER_SUCCEEDS")
                val cursorMapper = (invocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(variableArguments)

                val lineStart = mapToPosition(0)
                val lineStartNext = mapToPosition(1)

                val gaps = cursorMapper.getSourceGaps()
                val result = ArrayList<FileContentReplacer.Replacing>(gaps.size + 1)
                result += FileContentReplacer.Replacing(lineStart.line, lineStartNext.line, lineStart.character, lineStartNext.character, "")
                return gaps.mapIndexedTo(result) { index, gap ->
                    val start = mapToPosition(gap.start)
                    val end = mapToPosition(gap.end)
                    val replacement = variableArguments[index]
                    FileContentReplacer.Replacing(start.line, end.line, start.character, end.character, replacement)
                }.iterator()
            }
            return null
        }
    }

    class ElementPosition(var cursor: Int, override var line: Int, override var char: Int?, val lines: List<String>): Positionable {
        companion object {
            fun fromCursor(cursor: Int, lines: List<String>): ElementPosition {
                val pos = AnalyzingResult.getPositionFromCursor(cursor, lines, false)
                return ElementPosition(cursor, pos.line, pos.character, lines)
            }
        }

        fun copy() = ElementPosition(cursor, line, char, lines)
        override fun setPos(line: Int, char: Int?) {
            this.line = line
            this.char = char
            cursor = AnalyzingResult.getCursorFromPosition(lines, Position(line, char ?: 0), false)
        }
    }
}