package net.papierkorb2292.command_crafter.editor.debugger.server

import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebuggerVisualContext
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.helper.get
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ArgumentBreakpointParserSupplier
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointAction
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionPauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ServerCommandSourceValueReference
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.mixin.editor.debugger.CommandElementAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.CommandFunctionManagerEntryAccessor
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.Scope
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*

class FunctionElementDebugInformation(
    private val commands: List<Pair<ParseResults<ServerCommandSource>, Int>>,
    private val lines: List<String>,
    private val conditionParser: BreakpointConditionParser,
    private val sourceFunctionFile: Identifier
) : FunctionDebugInformation {
    companion object {
        private const val COMMAND_SOURCE_SCOPE_NAME = "Command-Source"
    }

    private val elementRanges = commands.map {
        val firstContext = it.first.context
        val cursorOffset = it.second
        StringRange(
            firstContext.range.start + cursorOffset,
            firstContext.lastChild.range.end + cursorOffset
        )
    }
    private val commandCursorMap = commands.toMap()
    private var functionStackFrameRange = Range(Position(), Position())

    fun setFunctionStringRange(stringRange: StringRange) {
        functionStackFrameRange = Range(
            AnalyzingResult.getPositionFromCursor(stringRange.start, lines, false),
            AnalyzingResult.getPositionFromCursor(stringRange.end, lines, false)
        )
    }

    var functionId: Identifier? = null

    override fun parseBreakpoints(
        breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
        server: MinecraftServer
    ): List<Breakpoint> {
        val result: MutableList<Breakpoint> = ArrayList()
        var currentElementIndex = 0
        val functionId = functionId ?: return emptyList()
        breakpoints@while(breakpoints.isNotEmpty()) {
            val breakpoint = breakpoints.peek()
            val sourceBreakpoint = breakpoint.unparsed.sourceBreakpoint
            val column = sourceBreakpoint.column
            if(column == null) {
                val breakpointRange = AnalyzingResult.getLineCursorRange(sourceBreakpoint.line, lines)
                while(currentElementIndex < elementRanges.size) {
                    val comparedToCurrentElement =
                        breakpointRange.compareTo(elementRanges[currentElementIndex])
                    if(comparedToCurrentElement <= 0) {
                        breakpoints.poll()
                        result += if(comparedToCurrentElement == 0) {
                            val parseResults = commands[currentElementIndex].first
                            breakpoint.action = BreakpointAction(
                                FunctionBreakpointLocation(functionId, parseResults),
                                conditionParser.parseCondition(
                                    sourceBreakpoint.condition,
                                    sourceBreakpoint.hitCondition
                                )
                            )
                            MinecraftDebuggerServer.acceptBreakpoint(breakpoint.unparsed)
                        } else {
                            MinecraftDebuggerServer.rejectBreakpoint(
                                breakpoint.unparsed,
                                MinecraftDebuggerServer.BREAKPOINT_AT_NO_CODE_REJECTION_REASON
                            )
                        }
                        continue@breakpoints
                    }
                    currentElementIndex++
                }
                break
            }
            val breakpointCursor = AnalyzingResult.getCursorFromPosition(
                lines,
                Position(sourceBreakpoint.line, sourceBreakpoint.column),
                false
            )
            // Find the next element containing the breakpoint
            while(currentElementIndex < elementRanges.size) {
                val comparedToCurrentElement =
                    breakpointCursor.compareTo(elementRanges[currentElementIndex])
                if(comparedToCurrentElement <= 0) {
                    breakpoints.poll()
                    if(comparedToCurrentElement == 0) {
                        // The element contains the breakpoint
                        val command = commands[currentElementIndex]
                        var context = command.first.context
                        val relativeBreakpointCursor = breakpointCursor - command.second
                        // Find the context containing the breakpoint
                        contexts@while(context != null) {
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
                                        val argument = context.arguments[node.name]
                                        if(type is ArgumentBreakpointParserSupplier && argument != null) {
                                            val breakpointList =
                                                type.`command_crafter$getBreakpointParser`(argument.result, server)?.parseBreakpoints(
                                                    breakpoints, server
                                                )
                                            if (!breakpointList.isNullOrEmpty()) {
                                                result += breakpointList
                                                continue@breakpoints
                                            }
                                        }
                                    }
                                    breakpoint.action = BreakpointAction(
                                        FunctionBreakpointLocation(functionId, context, command.first),
                                        conditionParser.parseCondition(
                                            sourceBreakpoint.condition,
                                            sourceBreakpoint.hitCondition
                                        )
                                    )
                                    result += MinecraftDebuggerServer.acceptBreakpoint(breakpoint.unparsed)
                                    continue@breakpoints
                                }
                            }
                            context = context.child
                        }
                    }
                    // The element is after the breakpoint (meaning no previous element
                    // contained the breakpoint) or the current element contained the
                    // breakpoint, but no nodes of the breakpoint
                    result += MinecraftDebuggerServer.rejectBreakpoint(
                        breakpoint.unparsed,
                        MinecraftDebuggerServer.BREAKPOINT_AT_NO_CODE_REJECTION_REASON
                    )
                    continue@breakpoints
                }
                currentElementIndex++
            }
            break
        }
        return result
    }

    override fun createDebugPauseHandler(pauseContext: FunctionPauseContext) = object : DebugPauseHandler {
        private var nextCommand: ParseResults<ServerCommandSource>? = null
        private var nextCommandSection: Int? = null

        private var shouldGoToPreviousSection: Boolean = false

        private var preStepInCommandSection: Int? = null
        private var preStepInContextIndex: Int? = null

        override fun next(granularity: SteppingGranularity) {
            if(granularity == SteppingGranularity.LINE) {
                TODO("Not yet implemented")
            }
            val command = (pauseContext.currentCommand as CommandElementAccessor).parsed
            val sectionIndex = pauseContext.currentSectionIndex
            val currentCommandContexts = pauseContext.contextStack[pauseContext.indexOfCurrentSectionInContextStack]
            if(currentCommandContexts.hasNextInSameGroup()) {
                // There is another context in the current section
                nextCommand = command
                nextCommandSection = sectionIndex
                pauseContext.pauseAtCommandSection(command, sectionIndex)
                return
            }
            if(!pauseAtPreviousSection()) {
                pauseAtNextCommand()
            }
        }
        override fun stepIn(granularity: SteppingGranularity) {
            val command = (pauseContext.currentCommand as CommandElementAccessor).parsed
            val currentContextBuilder = command.context[pauseContext.currentSectionIndex]
            if(currentContextBuilder != null) {
                val nextContextBuilder = currentContextBuilder.child
                if (nextContextBuilder != null) {
                    nextCommand = command
                    nextCommandSection = pauseContext.currentSectionIndex + 1
                    preStepInCommandSection = pauseContext.currentSectionIndex
                    preStepInContextIndex = pauseContext.contextStack[pauseContext.indexOfCurrentSectionInContextStack].currentContextIndex
                    pauseContext.pauseAtCommandSection(command, pauseContext.currentSectionIndex + 1)
                    return
                }
                val currentParsedNodes = currentContextBuilder.nodes
                if(currentParsedNodes.isNotEmpty()) {
                    val firstNode = currentParsedNodes[0].node
                    if(firstNode is LiteralCommandNode<*>
                        && firstNode.literal == "function"
                        && command.context.rootNode.children.contains(firstNode)
                        ) {
                        pauseContext.stepIntoFunctionCall()
                        return
                    }
                }
            }
            next(SteppingGranularity.STATEMENT)
        }

        override fun shouldStopOnCurrentContext(): Boolean {
            // In case a stepIn was performed (=the preStepIn location fields are set)
            // the desired child contexts might have been skipped and the pause happened
            // on the next context in that section. Before pausing on that context,
            // the debugee can traverse contexts, which according to the context tree,
            // should be viewed first.
            val preStepInCommandSection = preStepInCommandSection
            val preStepInContextIndex = preStepInContextIndex
            val nextCommand = nextCommand
            if(preStepInCommandSection != null && preStepInContextIndex != null && nextCommand != null) {
                this.preStepInCommandSection = null
                this.preStepInContextIndex = null
                var contextIndex: Int? = pauseContext.contextStack[pauseContext.indexOfCurrentSectionInContextStack].currentContextIndex
                val indexOfCommandInContextStack = pauseContext.indexOfCurrentSectionInContextStack - pauseContext.currentSectionIndex
                for(i in pauseContext.indexOfCurrentSectionInContextStack downTo (indexOfCommandInContextStack + preStepInCommandSection + 1)) {
                    val sectionContexts = pauseContext.contextStack[i]
                    contextIndex = sectionContexts.getGroupIndexOfContext(contextIndex ?: break)
                }
                if(contextIndex != preStepInContextIndex) {
                    // The desired contexts were skipped
                    for(i in preStepInCommandSection downTo 0) {
                        if(pauseContext.contextStack[i + indexOfCommandInContextStack].hasNextInSameGroup()) {
                            nextCommandSection = i
                            pauseContext.pauseAtCommandSection(nextCommand, i)
                            return false
                        }
                    }
                }
            }

            if(shouldGoToPreviousSection && nextCommand != null) {
                shouldGoToPreviousSection = false
                val nextCommandSection = nextCommandSection
                if(nextCommandSection != null) {
                    pauseContext.pauseAtCommandSection(nextCommand, nextCommandSection)
                    return false
                }
            }
            return true
        }
        override fun stepOut(granularity: SteppingGranularity) {
            if(!pauseAtPreviousSection()) {
                pauseContext.stepOutOfFunction()
            }
        }
        override fun continue_() {
            pauseContext.continue_()
        }

        override fun findNextPauseLocation() {
            if(!pauseAtPreviousSection()) {
                pauseAtNextCommand()
            }
        }

        override fun onBreakpoint() {
            val parseResults = (pauseContext.currentCommand as CommandElementAccessor).parsed
            nextCommand = parseResults
            nextCommandSection = pauseContext.currentSectionIndex
        }

        fun pauseAtPreviousSection(): Boolean {
            val sectionIndex = pauseContext.currentSectionIndex
            val command = (pauseContext.currentCommand as CommandElementAccessor).parsed

            val contextStack = pauseContext.contextStack
            val contextStackIndexAtCommandStart = pauseContext.indexOfCurrentSectionInContextStack - sectionIndex
            //Iterate over the contexts of previous section of the command
            for(prevSectionIndex in (sectionIndex - 1) downTo 0) {
                val contexts = contextStack[contextStackIndexAtCommandStart + prevSectionIndex]
                if(!contexts.hasNextInSameGroup()) continue
                nextCommand = command
                nextCommandSection = prevSectionIndex
                shouldGoToPreviousSection = true
                pauseContext.pauseAtCommandSection(command, pauseContext.currentSectionIndex)
                return true
            }
            return false
        }
        fun pauseAtNextCommand() {
            val nextCommands = pauseContext.executionQueue.iterator()
            while(nextCommands.hasNext()) {
                val nextCommand = nextCommands.next()
                val nextElement = (nextCommand as CommandFunctionManagerEntryAccessor).element
                if(nextElement is CommandElementAccessor) {
                    val parsed = nextElement.parsed
                    this.nextCommand = parsed
                    nextCommandSection = 0
                    pauseContext.pauseAtCommandSection(parsed, 0)
                    return
                }
            }
            pauseContext.stepOutOfFunction()
        }

        override fun getStackFrames(): List<MinecraftStackFrame> {
            val command = nextCommand ?: return emptyList()
            val section = command.context[nextCommandSection ?: return emptyList()]
            val cursorOffset = commandCursorMap[command] ?: 0
            val indexOfCommandInContextStack = pauseContext.indexOfCurrentSectionInContextStack - pauseContext.currentSectionIndex

            fun createServerCommandSourceScope(source: ServerCommandSource, setter: ((ServerCommandSource) -> Unit)? = null): Scope {
                val variablesReferencer = ServerCommandSourceValueReference(pauseContext, source, setter)
                return Scope().apply {
                    name = COMMAND_SOURCE_SCOPE_NAME
                    variablesReference = pauseContext.addVariablesReferencer(variablesReferencer)
                    namedVariables = variablesReferencer.namedVariableCount
                    indexedVariables = variablesReferencer.indexedVariableCount
                }
            }

            val stackFrames = ArrayList<MinecraftStackFrame>(pauseContext.currentSectionIndex + 2)
            stackFrames += MinecraftStackFrame(
                functionId.toString(),
                DebuggerVisualContext(
                    PackContentFileType.FunctionsFileType,
                    sourceFunctionFile,
                    functionStackFrameRange,
                    null
                ),
                // When updating to 1.20.2, this can also contain macros
                arrayOf(
                    createServerCommandSourceScope(pauseContext.contextStack[indexOfCommandInContextStack].currentContext.source)
                )
            )
            for(i in (0..pauseContext.currentSectionIndex + 1)) {
                val frameSection = command.context[i] ?: break
                val frameSectionContexts = pauseContext.contextStack[indexOfCommandInContextStack + i]
                val stringRange = frameSection.range
                val firstParsedNode = frameSection.nodes.firstOrNull()
                stackFrames += MinecraftStackFrame(
                    firstParsedNode?.node?.toString() ?: "<null>",
                    DebuggerVisualContext(
                        PackContentFileType.FunctionsFileType,
                        sourceFunctionFile,
                        Range(
                            AnalyzingResult.getPositionFromCursor(stringRange.start + cursorOffset, lines, false),
                            AnalyzingResult.getPositionFromCursor(stringRange.end + cursorOffset, lines, false)
                        ),
                        null
                    ),
                    arrayOf(
                        if(pauseContext.contextStack.size == indexOfCommandInContextStack + i + 1)
                            createServerCommandSourceScope(frameSectionContexts.currentContext.source) {
                                frameSectionContexts.currentContext = frameSectionContexts.currentContext.copyFor(it)
                            }
                        else
                            createServerCommandSourceScope(frameSectionContexts.currentContext.source)
                    )
                )
                if(frameSection == section) break
            }

            return stackFrames
        }
    }
}
