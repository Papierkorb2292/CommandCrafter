package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.command.SingleCommandAction
import net.minecraft.command.argument.CommandFunctionArgumentType.FunctionArgument
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
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.*
import net.papierkorb2292.command_crafter.editor.debugger.variables.StringMapValueReference
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper. compareTo
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
        private const val FUNCTION_MACROS_SCOPE_NAME = "Macros"
        private const val STEP_IN_NEXT_SECTION_BEGINNING_LABEL = "Next section: beginning"
        private const val STEP_IN_NEXT_SECTION_CURRENT_SOURCE_LABEL = "Next section: follow context"
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
        sourceReference: Int?,
        debugConnection: EditorDebugConnection
    ): List<Breakpoint> {
        val functionId = functionId ?: return emptyList()
        val result: MutableList<Breakpoint> = ArrayList()
        val addedBreakpoints = BreakpointManager.AddedBreakpointList<FunctionBreakpointLocation>()
        dynamicBreakpoints.clear()
        for(element in elements) {
            element.parseBreakpoints(breakpoints, server, this, result, addedBreakpoints, dynamicBreakpoints, sourceReference, debugConnection)
        }
        server.getDebugManager().functionDebugHandler.updateBreakpointParserBreakpoints(functionId, sourceReference, debugConnection, this, addedBreakpoints)
        return result
    }
    override fun createDebugPauseHandler(debugFrame: FunctionDebugFrame) = FunctionElementDebugPauseHandler(debugFrame)

    inner class FunctionElementDebugPauseHandler(val debugFrame: FunctionDebugFrame) : DebugPauseHandler, FileContentReplacer {
        val dynamicElementPositions = mutableMapOf<CommandContext<ServerCommandSource>, MutableMap<Int?, ElementPosition>>()

        private val sourceReferences = mutableSetOf<Int>()

        private var stepTargetSourceIndex: Int? = null
        private var stepTargetSourceSection: Int? = null

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
                stepTargetSourceSection = debugFrame.currentSectionIndex
                stepTargetSourceIndex = debugFrame.currentSectionSources.currentSourceIndex + 1
                debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, debugFrame.currentSectionIndex)
                return
            }
            pauseAtNextCommand()
        }
        override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
            val currentContext = debugFrame.currentContextChain[debugFrame.currentSectionIndex]
            if(currentContext != null && currentContext.child == null && (currentContext.command as? PotentialDebugFrameInitiator)?.`command_crafter$willInitiateDebugFrame`() == true) {
                debugFrame.pauseContext.stepIntoFrame()
                return
            }
            if(debugFrame.hasNextSection()) {
                debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, getNextCommandSection())
                return
            }
            next(granularity)
        }

        override fun shouldStopOnCurrentContext(): Boolean {
            stepTargetSourceIndex?.let { targetSourceIndex ->
                var sourceIndex = debugFrame.currentSectionSources.currentSourceIndex
                stepTargetSourceSection?.let {
                    val contextChain = debugFrame.currentContextChain
                    for(i in debugFrame.currentSectionIndex downTo it + 1) {
                        if(!contextChain[i]!!.isDebuggable()) continue
                        sourceIndex = debugFrame.sectionSources[i].parentSourceIndices[sourceIndex]
                    }
                }
                if(sourceIndex == targetSourceIndex) {
                    stepTargetSourceIndex = null
                    stepTargetSourceSection = null
                    return true
                }
                if(sourceIndex > targetSourceIndex) {
                    stepTargetSourceIndex = null
                    stepTargetSourceSection = null
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
            val contextChain = debugFrame.currentContextChain

            val sectionIndex = frameId - 1
            val sectionContext = contextChain.topContext.getExcludeEmpty(sectionIndex)
                ?: return CompletableFuture.completedFuture(StepInTargetsResponse())

            val targets = mutableListOf<StepInTarget>()
            val targetsManager = debugFrame.pauseContext.stepInTargetsManager
            val executable = (debugFrame.currentContextChain as ContextChainAccessor<*>).executable
            if(sectionContext == executable) {
                if((executable.command as? PotentialDebugFrameInitiator)?.`command_crafter$willInitiateDebugFrame`() == true) {
                    targets += StepInTarget().also {
                        it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                            debugFrame.pauseContext.stepIntoFrame()
                        })
                        it.label = buildStepInCurrentLabel(executable)
                    }
                }
            } else {
                val nextSectionIndex = getNextCommandSection()
                targets += StepInTarget().also {
                    it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                        debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, nextSectionIndex)
                    })
                    it.label = STEP_IN_NEXT_SECTION_BEGINNING_LABEL
                }
                val currentSourceIndex = debugFrame.currentSectionSources.currentSourceIndex
                targets += StepInTarget().also {
                    it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                        stepTargetSourceSection = nextSectionIndex
                        stepTargetSourceIndex = currentSourceIndex
                        debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, nextSectionIndex)
                    })
                    it.label = STEP_IN_NEXT_SECTION_CURRENT_SOURCE_LABEL
                }
                val nextSectionSources = debugFrame.sectionSources[debugFrame.currentSectionIndex + 1]
                if((sectionContext.redirectModifier as? PotentialDebugFrameInitiator)?.`command_crafter$willInitiateDebugFrame`() == true
                    && (nextSectionSources.parentSourceIndices.isEmpty()
                            || nextSectionSources.parentSourceIndices.last() < currentSourceIndex)) {

                    targets += StepInTarget().also {
                        it.id = targetsManager.addStepInTarget(StepInTargetsManager.Target {
                            debugFrame.pauseContext.stepIntoFrame()
                        })
                        it.label = buildStepInCurrentLabel(sectionContext)
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

        private fun haveChildSourcesBeenGenerated(): Boolean { //TODO ?
            val sectionSourcesList = debugFrame.sectionSources
            if(sectionSourcesList.size <= debugFrame.currentSectionIndex + 1) return false
            val nextSectionSources = sectionSourcesList[debugFrame.currentSectionIndex + 1]
            return nextSectionSources.parentSourceIndices.last() >= debugFrame.currentSectionSources.currentSourceIndex - 1
        }

        override fun findNextPauseLocation() {
            /*if((debugFrame.currentContext.redirectModifier as? PotentialDebugFrameInitiator)?.`command_crafter$willInitiateDebugFrame`() == true && haveChildSourcesBeenGenerated()) {
                debugFrame.onReachedPauseLocation()
                return
            }*/
            if(debugFrame.sectionSources.isEmpty() || debugFrame.currentSectionSources.hasCurrent()) {
                debugFrame.pauseAtSection(debugFrame.currentContextChain.topContext, debugFrame.currentSectionIndex)
                return
            }
            if(debugFrame.currentCommandIndex + 1 >= debugFrame.contextChains.size) {
                debugFrame.pauseContext.pauseAfterExitFrame()
                return
            }
            debugFrame.pauseAtSection(debugFrame.contextChains[debugFrame.currentCommandIndex + 1].topContext, 0)
        }

        fun pauseAtNextCommand() {
            if (debugFrame.currentCommandIndex + 1 >= debugFrame.contextChains.size) {
                debugFrame.pauseContext.pauseAfterExitFrame()
                return
            }
            debugFrame.pauseAtSection(debugFrame.contextChains[debugFrame.currentCommandIndex + 1].topContext, 0)
        }

        fun getNextCommandSection(): Int {
            var nextSectionIndex = debugFrame.currentSectionIndex + 1
            var nextContext = debugFrame.currentContext.child
            while(nextContext.redirectModifier == null && nextContext.command == null) {
                nextContext = nextContext.child
                if(nextContext == null) return nextSectionIndex
                nextSectionIndex++
            }
            return nextSectionIndex
        }

        override fun getStackFrames(sourceReference: Int?): List<MinecraftStackFrame> {
            val contextChain = debugFrame.currentContextChain
            val cursorOffset = getCursorOffset(contextChain.topContext, sourceReference) ?: return emptyList()

            fun createServerCommandSourceScope(source: ServerCommandSource, setter: ((ServerCommandSource) -> Unit)? = null): Scope {
                val variablesReferencer = ServerCommandSourceValueReference(debugFrame.pauseContext.variablesReferenceMapper, source, setter)
                return Scope().apply {
                    name = COMMAND_SOURCE_SCOPE_NAME
                    variablesReference = variablesReferencer.getVariablesReferencerId()
                    namedVariables = variablesReferencer.namedVariableCount
                    indexedVariables = variablesReferencer.indexedVariableCount
                }
            }

            val macrosVariableReferencer = StringMapValueReference(debugFrame.pauseContext.variablesReferenceMapper, debugFrame.macroNames.zip(debugFrame.macroArguments).toMap())
            val macrosScope = Scope().apply {
                name = FUNCTION_MACROS_SCOPE_NAME
                variablesReference = macrosVariableReferencer.getVariablesReferencerId()
                namedVariables = macrosVariableReferencer.namedVariableCount
                indexedVariables = macrosVariableReferencer.indexedVariableCount
            }

            val source = Source().apply {
                name = FunctionDebugHandler.getSourceName(sourceFunctionFile)
                path = PackContentFileType.FUNCTIONS_FILE_TYPE.toStringPath(sourceFunctionFile)
            }

            val stackFrames = ArrayList<MinecraftStackFrame>(debugFrame.currentSectionIndex + 2)
            stackFrames += MinecraftStackFrame(
                functionId.toString(),
                DebuggerVisualContext(
                    source,
                    functionStackFrameRange
                ),
                arrayOf(
                    createServerCommandSourceScope(debugFrame.sectionSources[0].sources[0]),
                    macrosScope
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
                            createServerCommandSourceScope(debugFrame.sectionSources[sectionIndex].sources[sourceIndex]) {
                                debugFrame.currentSource = it
                            }
                        else
                            createServerCommandSourceScope(debugFrame.sectionSources[sectionIndex].sources[sourceIndex]),
                        macrosScope
                    )
                ))
            }

            @Suppress("UNCHECKED_CAST")
            val modifiers = (contextChain as ContextChainAccessor<ServerCommandSource>).modifiers
            var lastRunningModifier = debugFrame.currentSectionIndex
            var sourceIndex = debugFrame.currentSectionSources.currentSourceIndex

            if(debugFrame.pauseContext.peekDebugFrame() != debugFrame)
                sourceIndex--

            if(debugFrame.currentSectionIndex == modifiers.size) {
                @Suppress("UNCHECKED_CAST")
                addStackFrameForSection((contextChain as ContextChainAccessor<ServerCommandSource>).executable, debugFrame.currentSectionIndex, sourceIndex)
                if(lastRunningModifier == 0) {
                    return stackFrames
                }
                lastRunningModifier -= 1
                sourceIndex = debugFrame.sectionSources[debugFrame.currentSectionIndex].parentSourceIndices[sourceIndex]
            }

            for(i in lastRunningModifier downTo 0) {
                if(modifiers[i].redirectModifier == null) {
                    continue
                }
                addStackFrameForSection(modifiers[i], i, sourceIndex)
                if(i > 0) {
                    sourceIndex = debugFrame.sectionSources[i].parentSourceIndices[sourceIndex]
                }
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
            addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionBreakpointLocation>,
            dynamics: MutableMap<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>,
            sourceReference: Int?,
            debugConnection: EditorDebugConnection?
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
        private val argumentBreakpointParserSuppliers: Map<ArgumentBreakpointParserSupplier, Any>
        init {
            val argumentBreakpointParserSuppliers = mutableMapOf<ArgumentBreakpointParserSupplier, Any>()
            var context: CommandContext<ServerCommandSource>? = rootContext
            while(context != null) {
                for(parsedNode in context.nodes) {
                    val node = parsedNode.node
                    if(node is ArgumentCommandNode<*, *>) {
                        val type = node.type
                        if(type is ArgumentBreakpointParserSupplier) {
                            argumentBreakpointParserSuppliers[type] = context.getArgument(node.name, FunctionArgument::class.java)
                        }
                    }
                }
                context = context.child
            }
            this.argumentBreakpointParserSuppliers = argumentBreakpointParserSuppliers
        }

        override fun parseBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            debugInformation: FunctionElementDebugInformation,
            parsed: MutableList<Breakpoint>,
            addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionBreakpointLocation>,
            dynamics: MutableMap<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>,
            sourceReference: Int?,
            debugConnection: EditorDebugConnection?
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
            val emptyArgumentBreakpointParserSuppliers = argumentBreakpointParserSuppliers.toMutableMap()
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
                        if((context.redirectModifier == null && context.command == null) ||
                            (context.redirectModifier as? ForkableNoPauseFlag)?.`command_crafter$cantPause`() == true) {

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
                                if(sourceReference == INITIAL_SOURCE_REFERENCE && debugConnection != null && node is ArgumentCommandNode<*, *>) {
                                    val type = node.type
                                    val argument = context.getArgument(node.name, FunctionArgument::class.java)
                                    if(type is ArgumentBreakpointParserSupplier) {
                                        emptyArgumentBreakpointParserSuppliers -= type
                                        val breakpointList =
                                            type.`command_crafter$getBreakpointParser`(argument, server)?.parseBreakpoints(
                                                breakpoints, server, null, debugConnection
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
                                addedBreakpoints.list += breakpoint
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
                addedBreakpoints.list += breakpoint
            }
            if(sourceReference == INITIAL_SOURCE_REFERENCE && debugConnection != null) {
                for((supplier, arg) in emptyArgumentBreakpointParserSuppliers) {
                    supplier.`command_crafter$getBreakpointParser`(arg, server)
                        ?.parseBreakpoints(LinkedList(), server, sourceReference, debugConnection)
                }
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
            addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionBreakpointLocation>,
            dynamics: MutableMap<FunctionElementProcessor, List<ServerBreakpoint<FunctionBreakpointLocation>>>,
            sourceReference: Int?,
            debugConnection: EditorDebugConnection?
        ) {
            if(sourceReference == INITIAL_SOURCE_REFERENCE) {
                parseInitialSourceBreakpoints(breakpoints, server, debugInformation, parsed, addedBreakpoints, dynamics)
                return
            }
            val pauseHandler = debugInformation.sourceReferenceDebugHandlers[sourceReference] ?: return
            val frame = pauseHandler.debugFrame
            (frame.procedure.entries()[elementIndex] as? SingleCommandAction.Sourced)?.let { action ->
                @Suppress("UNCHECKED_CAST")
                val context = (action as SingleCommandActionAccessor<ServerCommandSource>).contextChain.topContext
                val cursorOffset = pauseHandler.dynamicElementPositions[context]?.get(sourceReference)?.cursor ?: return
                CommandContextElementProcessor(context, cursorOffset) {
                    val lines = server.getDebugManager()
                        .getSourceReferenceLines(it.debugConnection, sourceReference)
                        ?: debugInformation.lines
                    getFileBreakpointRange(it, lines)
                }.parseBreakpoints(
                    breakpoints,
                    server,
                    debugInformation,
                    parsed,
                    addedBreakpoints,
                    mutableMapOf(),
                    INITIAL_SOURCE_REFERENCE,
                    null
                )
            }
        }

        private fun parseInitialSourceBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            debugInformation: FunctionElementDebugInformation,
            parsed: MutableList<Breakpoint>,
            addedBreakpoints: BreakpointManager.AddedBreakpointList<FunctionBreakpointLocation>,
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
                        addedBreakpoints.list += breakpoint
                        continue@breakpoints
                    }
                    // The element is after the breakpoint (meaning no previous element
                    // contained the breakpoint)
                    breakpoints.poll()
                    parsed += MinecraftDebuggerServer.rejectBreakpoint(
                        breakpoint.unparsed,
                        MinecraftDebuggerServer.BREAKPOINT_AT_NO_CODE_REJECTION_REASON
                    )
                    addedBreakpoints.list += breakpoint
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
                    BreakpointManager.AddedBreakpointList(),
                    mutableMapOf(),
                    INITIAL_SOURCE_REFERENCE,
                    null
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
            if(path != PackContentFileType.FUNCTIONS_FILE_TYPE.toStringPath(debugInformation.sourceFunctionFile))
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