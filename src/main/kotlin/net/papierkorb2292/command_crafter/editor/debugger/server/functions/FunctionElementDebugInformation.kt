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
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.helper.arrayOfNotNull
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ContextChainAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.SingleCommandActionAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.VariableLineAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator
import net.papierkorb2292.command_crafter.parser.helper.*
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
    private val reader: DirectiveStringReader<ParsedResourceCreator>,
    private val conditionParser: BreakpointConditionParser,
    private val sourceFunctionFile: Identifier
) : FunctionDebugInformation {
    companion object {
        private const val COMMAND_SOURCE_SCOPE_NAME = "Command-Source"
        private const val FUNCTION_MACROS_SCOPE_NAME = "Macros"
        private const val COMMAND_RESULT_SCOPE_NAME = "Command-Result"
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

    private val sourceReferenceDebugHandlers = mutableMapOf<Int, FunctionElementDebugPauseHandler>()

    val lines get() = reader.lines

    fun setFunctionStringRange(stringRange: StringRange) {
        functionStackFrameRange = Range(
            AnalyzingResult.getPositionFromCursor(stringRange.start, lines, false),
            AnalyzingResult.getPositionFromCursor(stringRange.end, lines, false)
        )
    }

    var functionId: Identifier? = null

    private fun getLinesForSourceReference(server: MinecraftServer, debugConnection: EditorDebugConnection, sourceReference: Int?) =
        server.getDebugManager().getSourceReferenceLines(debugConnection, sourceReference) ?: lines

    override fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
        server: MinecraftServer,
        sourceReference: Int?,
        debugConnection: EditorDebugConnection
    ): List<Breakpoint> {
        val functionId = functionId ?: return emptyList()
        val result: MutableList<Breakpoint> = ArrayList()
        val addedBreakpoints = BreakpointManager.AddedBreakpointList<FunctionBreakpointLocation>()
        if(sourceReference == INITIAL_SOURCE_REFERENCE)
            dynamicBreakpoints.clear()
        for(element in elements) {
            element.parseBreakpoints(breakpoints, server, this, result, addedBreakpoints, dynamicBreakpoints, sourceReference, debugConnection)
        }
        server.getDebugManager().functionDebugHandler.updateBreakpointParserBreakpoints(functionId, sourceReference, debugConnection, this, addedBreakpoints)
        return result
    }
    override fun createDebugPauseHandler(debugFrame: FunctionDebugFrame) = FunctionElementDebugPauseHandler(debugFrame)

    inner class FunctionElementDebugPauseHandler(val debugFrame: FunctionDebugFrame) : DebugPauseHandler, FileContentReplacer {
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
                //frameId == 0 is the function frame, which doesn't have any step in targets
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
                if((sectionContext.redirectModifier as? PotentialDebugFrameInitiator)?.`command_crafter$willInitiateDebugFrame`() == true
                    && FunctionDebugFrame.commandResult.get() == null) {

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

        override fun findNextPauseLocation() {
            if(FunctionDebugFrame.commandResult.get() != null) {
                debugFrame.onReachedPauseLocation()
                return
            }
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
            val lines = getLinesForSourceReference(debugFrame.pauseContext.server, debugFrame.pauseContext.debugConnection!!, sourceReference)
            val sourceReferenceCursorMapper = debugFrame.pauseContext.server.getDebugManager().getSourceReferenceCursorMapper(debugFrame.pauseContext.debugConnection!!, sourceReference)

            fun getContextRange(context: CommandContext<*>): Range {
                val firstParsedNode = context.nodes.first()
                val lastParsedNode = context.nodes.last()

                if((contextChain as IsMacroContainer).`command_crafter$getIsMacro`()) {
                    val absoluteMacroStartCursor = reader.cursorMapper.mapToSource(
                        (firstParsedNode as CursorOffsetContainer).getCursorOffset()
                    ) + 1
                    val sourceReferenceCursor = sourceReferenceCursorMapper?.mapToTarget(absoluteMacroStartCursor) ?: absoluteMacroStartCursor
                    return Range(
                        AnalyzingResult.getPositionFromCursor(
                            sourceReferenceCursor + firstParsedNode.range.start,
                            lines,
                            false
                        ),
                        AnalyzingResult.getPositionFromCursor(
                            sourceReferenceCursor + lastParsedNode.range.end,
                            lines,
                            false
                        )
                    )
                }
                val startAbsoluteCursor = reader.cursorMapper.mapToSource(
                    firstParsedNode.range.start + (firstParsedNode as CursorOffsetContainer).getCursorOffset()
                )
                val endAbsoluteCursor = reader.cursorMapper.mapToSource(
                    lastParsedNode.range.end + (lastParsedNode as CursorOffsetContainer).getCursorOffset()
                )

                return Range(
                    AnalyzingResult.getPositionFromCursor(
                        sourceReferenceCursorMapper?.mapToTarget(startAbsoluteCursor) ?: startAbsoluteCursor,
                        lines,
                        false
                    ),
                    AnalyzingResult.getPositionFromCursor(
                        sourceReferenceCursorMapper?.mapToTarget(endAbsoluteCursor) ?: endAbsoluteCursor,
                        lines,
                        false
                    )
                )
            }


            fun createServerCommandSourceScope(source: ServerCommandSource, setter: ((ServerCommandSource) -> Unit)? = null): Scope {
                val variablesReferencer = ServerCommandSourceValueReference(debugFrame.pauseContext.variablesReferenceMapper, source, setter)
                return Scope().apply {
                    name = COMMAND_SOURCE_SCOPE_NAME
                    variablesReference = variablesReferencer.getVariablesReferencerId()
                    namedVariables = variablesReferencer.namedVariableCount
                    indexedVariables = variablesReferencer.indexedVariableCount
                }
            }

            val macrosScope = if(debugFrame.macroArguments.isNotEmpty()) Scope().apply {
                val macrosVariableReferencer = StringMapValueReference(debugFrame.pauseContext.variablesReferenceMapper, debugFrame.macroNames.zip(debugFrame.macroArguments).toMap())
                name = FUNCTION_MACROS_SCOPE_NAME
                variablesReference = macrosVariableReferencer.getVariablesReferencerId()
                namedVariables = macrosVariableReferencer.namedVariableCount
                indexedVariables = macrosVariableReferencer.indexedVariableCount
            } else null

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
                arrayOfNotNull(
                    createServerCommandSourceScope(debugFrame.sectionSources[0].sources[0]),
                    macrosScope
                )
            )

            fun addStackFrameForSection(context: CommandContext<*>, sectionIndex: Int, sourceIndex: Int) {
                val firstParsedNode = context.nodes.firstOrNull()
                val showsCommandResult = sectionIndex == debugFrame.currentSectionIndex && FunctionDebugFrame.commandResult.get() != null
                val commandResultScope = if(showsCommandResult) Scope().apply {
                    val commandResult = FunctionDebugFrame.commandResult.get()!!
                    val commandResultValueReference = CommandResultValueReference(debugFrame.pauseContext.variablesReferenceMapper, commandResult) {
                        FunctionDebugFrame.commandResult.set(it)
                        it
                    }
                    name = COMMAND_RESULT_SCOPE_NAME
                    variablesReference = commandResultValueReference.getVariablesReferencerId()
                    namedVariables = commandResultValueReference.namedVariableCount
                    indexedVariables = commandResultValueReference.indexedVariableCount
                } else null
                val shownSourceIndex = if(!showsCommandResult) sourceIndex else sourceIndex - 1
                val contextRange = getContextRange(context)
                stackFrames.add(1, MinecraftStackFrame(
                    firstParsedNode?.node?.toString() ?: "<null>",
                    DebuggerVisualContext(
                        source,
                        if(showsCommandResult) Range(contextRange.end, contextRange.end.advance())
                        else contextRange
                    ),
                    arrayOfNotNull(
                        if (sectionIndex == debugFrame.currentSectionIndex)
                            createServerCommandSourceScope(debugFrame.sectionSources[sectionIndex].sources[shownSourceIndex]) {
                                debugFrame.currentSource = it
                            }
                        else
                            createServerCommandSourceScope(debugFrame.sectionSources[sectionIndex].sources[shownSourceIndex]),
                        macrosScope,
                        commandResultScope
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
                lastRunningModifier -= 1
                sourceIndex = debugFrame.sectionSources[debugFrame.currentSectionIndex].parentSourceIndices.getOrNull(sourceIndex) ?: return stackFrames
            }

            for(i in lastRunningModifier downTo 0) {
                if(modifiers[i].redirectModifier == null) {
                    continue
                }
                addStackFrameForSection(modifiers[i], i, sourceIndex)
                sourceIndex = debugFrame.sectionSources[i].parentSourceIndices.getOrNull(sourceIndex) ?: break
            }

            return stackFrames
        }

        override fun onExitFrame() {
            debugFrame.pauseContext.removeOnContinueListener(onReloadBreakpoints)
            sourceReferenceDebugHandlers -= sourceReferences
        }

        override fun getReplacementData(path: String): FileContentReplacer.ReplacementDataProvider {
            fun addSourceReference(sourceReference: Int) {
                sourceReferences += sourceReference
                sourceReferenceDebugHandlers[sourceReference] = this
            }

            val replacements = elements.asSequence().mapNotNull { it.getReplacements(path, debugFrame, this@FunctionElementDebugInformation)?.asSequence() }.flatten()
            return FileContentReplacer.ReplacementDataProvider(replacements, emptySequence(), ::addSourceReference)
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

        fun getReplacements(
            path: String,
            frame: FunctionDebugFrame,
            debugInformation: FunctionElementDebugInformation,
        ): Iterator<FileContentReplacer.Replacement>?
    }

    class CommandContextElementProcessor(
        val rootContext: CommandContext<ServerCommandSource>,
        val isMacro: Boolean = false,
        val breakpointRangeGetter: ((ServerBreakpoint<FunctionBreakpointLocation>, Int?) -> StringRange)? = null
    ) : FunctionElementProcessor {
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
            val useChildBreakpointParsers = debugConnection != null && (sourceReference == INITIAL_SOURCE_REFERENCE || !debugInformation.sourceReferenceDebugHandlers.containsKey(sourceReference))
            val lastContext = rootContext.lastChild
            val originalFunctionFileRange = debugInformation.reader.cursorMapper.mapToSource(StringRange(
                rootContext.range.start + (rootContext.nodes.first() as CursorOffsetContainer).getCursorOffset(),
                lastContext.range.end + (lastContext.nodes.last() as CursorOffsetContainer).getCursorOffset()
            ))
            val sourceReferenceCursorMapper = debugConnection?.let {
                server.getDebugManager().getSourceReferenceCursorMapper(it, sourceReference)
            }
            val sourceReferenceFunctionFileRange =
                sourceReferenceCursorMapper?.mapToTarget(originalFunctionFileRange) ?: originalFunctionFileRange
            val emptyArgumentBreakpointParserSuppliers = argumentBreakpointParserSuppliers.toMutableMap()
            breakpoints@while(breakpoints.isNotEmpty()) {
                val breakpoint = breakpoints.peek()
                val breakpointRange = breakpointRangeGetter?.invoke(breakpoint, sourceReference)
                    ?: getFileBreakpointRange(
                        breakpoint,
                        debugInformation.getLinesForSourceReference(server, breakpoint.debugConnection, sourceReference)
                    )
                val comparedToCurrentElement =
                    breakpointRange.compareTo(sourceReferenceFunctionFileRange)
                if(comparedToCurrentElement > 0) {
                    // The breakpoint is after the element
                    break
                }
                if(comparedToCurrentElement == 0) {
                    // The element contains the breakpoint
                    var context: CommandContext<ServerCommandSource>? = this.rootContext
                    var prevCursorOffset = (rootContext.nodes.first() as CursorOffsetContainer).getCursorOffset()
                    // Find the context containing the breakpoint
                    contexts@while(context != null) {
                        if((context.redirectModifier == null && context.command == null) ||
                            (context.redirectModifier as? ForkableNoPauseFlag)?.`command_crafter$cantPause`() == true) {

                            context = context.child
                            continue
                        }
                        for(parsedNode in context.nodes) {
                            val absoluteNodeRange =
                                if(!isMacro) {
                                    val nextCursorOffset = (parsedNode as CursorOffsetContainer).getCursorOffset()
                                    val range = debugInformation.reader.cursorMapper.mapToSource(StringRange(parsedNode.range.start + prevCursorOffset, parsedNode.range.end + nextCursorOffset))
                                    prevCursorOffset = nextCursorOffset
                                    range
                                } else {
                                    val start = debugInformation.reader.cursorMapper.mapToSource(prevCursorOffset)
                                    StringRange(start + parsedNode.range.start, start + parsedNode.range.end)
                                }
                            val comparedToNode = breakpointRange.compareTo(sourceReferenceCursorMapper?.mapToTarget(absoluteNodeRange) ?: absoluteNodeRange)
                            if(comparedToNode <= 0) {
                                if(comparedToNode < 0) {
                                    // The node is after the breakpoint
                                    // (meaning no previous node contained the breakpoint)
                                    break@contexts
                                }
                                //The node contains the breakpoint
                                val node = parsedNode.node
                                // The node could have its own breakpoint parser, but it should not be used
                                // when the breakpoints belong to a source reference that was created for the current
                                // function (because the child breakpoint parser already received breakpoints from the file
                                // it originally came from, so having other files contribute to the breakpoints would be confusing).
                                // However, breakpoints should be given to the child breakpoint parser if the source reference could
                                // belong to the child breakpoint parser.
                                if(debugConnection != null && node is ArgumentCommandNode<*, *>) {
                                    val type = node.type
                                    val argument = context.getArgument(node.name, Object::class.java)
                                    if(type is ArgumentBreakpointParserSupplier) {
                                        val parser = type.`command_crafter$getBreakpointParser`(argument, server)
                                        if(parser != null) {
                                            if(!useChildBreakpointParsers) {
                                                break@contexts // Reject breakpoint
                                            }
                                            emptyArgumentBreakpointParserSuppliers -= type
                                            val breakpointList = parser.parseBreakpoints(
                                                breakpoints, server, null, debugConnection
                                            )
                                            if(breakpointList.isNotEmpty()) {
                                                parsed += breakpointList
                                                continue@breakpoints
                                            }
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
            if(useChildBreakpointParsers && debugConnection != null) {
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

        override fun getReplacements(path: String, frame: FunctionDebugFrame, debugInformation: FunctionElementDebugInformation): Nothing? = null
    }

    class MacroElementProcessor(
        private val elementIndex: Int,
        private val macroFileRange: StringRange,
        private val macroLine: Macro.VariableLine<ServerCommandSource>,
        private val fileCursorMapper: SplitProcessedInputCursorMapper,
        private val macroSkippedChars: Int
    ): FunctionElementProcessor {
        init {
            (macroLine as CursorOffsetContainer).`command_crafter$setCursorOffset`(macroFileRange.start, macroSkippedChars)
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
            if(sourceReference == INITIAL_SOURCE_REFERENCE) {
                parseInitialSourceBreakpoints(breakpoints, server, debugInformation, parsed, addedBreakpoints, dynamics)
                return
            }
            val pauseHandler = debugInformation.sourceReferenceDebugHandlers[sourceReference] ?: return
            val frame = pauseHandler.debugFrame
            val action = frame.procedure.entries()[elementIndex] as? SingleCommandAction.Sourced ?: return
            @Suppress("UNCHECKED_CAST")
            val context = (action as SingleCommandActionAccessor<ServerCommandSource>).contextChain.topContext
            CommandContextElementProcessor(context, true)
                .parseBreakpoints(
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
                    breakpoints.poll()
                    if(comparedToCurrentElement == 0) {
                        // The element contains the breakpoint
                        dynamicBreakpoints += breakpoint
                        val parsedBreakpoint = MinecraftDebuggerServer.acceptBreakpoint(breakpoint.unparsed)
                        parsedBreakpoint.message = MinecraftDebuggerServer.DYNAMIC_BREAKPOINT_MESSAGE
                        parsed += parsedBreakpoint
                        addedBreakpoints.list += breakpoint
                        continue@breakpoints
                    }
                    // The element is after the breakpoint (meaning no previous element
                    // contained the breakpoint)
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
            val action = (frame.procedure.entries()[elementIndex] as? SingleCommandAction.Sourced) ?: return
            val invocation = (macroLine as VariableLineAccessor).invocation
            @Suppress("CAST_NEVER_SUCCEEDS")
            val resolvedMacroCursorMapper = (invocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(frame.macroArguments)

            @Suppress("UNCHECKED_CAST")
            val context = (action as SingleCommandActionAccessor<ServerCommandSource>).contextChain.topContext
            CommandContextElementProcessor(context, true) { breakpoint, _ ->
                val breakpointFileRange = getFileBreakpointRange(breakpoint, debugInformation.lines)
                val macroCursorOffset = macroFileRange.start - macroSkippedChars
                val unresolvedMacroRange = fileCursorMapper.mapToTarget(breakpointFileRange, true) - macroCursorOffset
                resolvedMacroCursorMapper.mapToTarget(unresolvedMacroRange - 1, true) + macroCursorOffset
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

        override fun getReplacements(path: String, frame: FunctionDebugFrame, debugInformation: FunctionElementDebugInformation): Iterator<FileContentReplacer.Replacement>? {
            if(path.endsWith(PackContentFileType.FUNCTIONS_FILE_TYPE.toStringPath(PackagedId(debugInformation.sourceFunctionFile, ""))))
                return null

            val action = frame.procedure.entries()[elementIndex] as? SingleCommandActionAccessor<*> ?: return null
            val commandString = action.contextChain.topContext.input

            val startPos = AnalyzingResult.getPositionFromCursor(macroFileRange.start, debugInformation.lines)
            val endPos = AnalyzingResult.getPositionFromCursor(macroFileRange.end, debugInformation.lines)

            val invocation = (macroLine as VariableLineAccessor).invocation
            val variableIndices = macroLine.dependentVariables
            val variableArguments = variableIndices.map { frame.macroArguments[it] }
            @Suppress("CAST_NEVER_SUCCEEDS")
            val cursorMapper = (invocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(variableArguments)

            return listOf(FileContentReplacer.Replacement(
                startPos.line,
                startPos.character,
                endPos.line,
                endPos.character,
                commandString,
                OffsetProcessedInputCursorMapper(macroFileRange.start)
                    .thenMapWith(fileCursorMapper)
                    .thenMapWith(OffsetProcessedInputCursorMapper(-(macroFileRange.start - macroSkippedChars)))
                    .thenMapWith(RemoveFirstCharProcessedInputCursorMapper) //Account for removal of '$'
                    .thenMapWith(cursorMapper)
            )).iterator()
        }
    }
}