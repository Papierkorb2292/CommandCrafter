package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.ContextChain
import com.mojang.datafixers.util.Either
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.functions.InstantiatedFunction
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.server.FileContentReplacer
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext.Companion.currentPauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.PositionableBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ContextChainAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.BuildContextsAccessor
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory

class FunctionDebugFrame(
    val pauseContext: PauseContext,
    val procedure: InstantiatedFunction<CommandSourceStack>,
    private val debugPauseHandlerFactory: FunctionDebugPauseHandlerFactory,
    val macroNames: List<String>,
    val macroArguments: List<String>,
    val unpauseCallback: () -> Unit,
    val sourceFileId: Identifier,
    val functionLines: List<String>
) : PauseContext.DebugFrame, CommandFeedbackConsumer {
    companion object {
        val sourceReferenceCursorMapper = mutableMapOf<Pair<EditorDebugConnection, Int>, ProcessedInputCursorMapper>()
        fun getCommandInfo(context: CommandContext<CommandSourceStack>): CommandInfo? {
            val pauseContext = currentPauseContext.get() ?: return null
            val debugFrame = pauseContext.peekDebugFrame() as? FunctionDebugFrame ?: return null
            return debugFrame.getCommandInfo(context)
        }

        fun checkSimpleActionPause(context: CommandContext<CommandSourceStack>, source: CommandSourceStack, commandInfo: CommandInfo? = null) {
            val pauseContext = currentPauseContext.get() ?: return
            val debugFrame = pauseContext.peekDebugFrame() as? FunctionDebugFrame ?: return
            val resolvedCommandInfo = commandInfo ?: debugFrame.getCommandInfo(context) ?: return
            debugFrame.currentSectionIndex = resolvedCommandInfo.sectionOffset
            debugFrame.checkPause(
                resolvedCommandInfo,
                context,
                source
            )
            val sectionSources = debugFrame.currentSectionSources
            sectionSources.currentSourceIndex += 1
        }
    }

    @Suppress("UNCHECKED_CAST")
    val contextChains: List<ContextChain<CommandSourceStack>> =
        procedure.entries().mapNotNull {
            (it as? BuildContextsAccessor<CommandSourceStack>)?.command
        }

    var commandFeedbackConsumer: CommandFeedbackConsumer? = null

    var currentCommandIndex = 0

    var currentSectionIndex = 0
    var sectionSources: MutableList<SectionSources> = mutableListOf()
    val currentContextChain: ContextChain<CommandSourceStack>
        get() = contextChains[currentCommandIndex]

    val currentContext: CommandContext<CommandSourceStack>
        get() = currentContextChain[currentSectionIndex]!!
    val currentSectionSources: SectionSources
        get() = sectionSources[currentSectionIndex]
    var currentSource: CommandSourceStack
        get() = currentSectionSources.currentSource
        set(value) {
            currentSectionSources.currentSource = value
        }

    private val sourceFilePackagedId = PackagedId(sourceFileId, "")
    private val sourceFilePackagedIdWithoutExtension = sourceFilePackagedId.removeExtension(FunctionDebugHandler.FUNCTION_FILE_EXTENSTION) ?: throw IllegalArgumentException("Source file id $sourceFileId doesn't have .mcfunction extension")
    private val sourceFilePath = PackContentFileType.FUNCTIONS_FILE_TYPE.toStringPath(sourceFilePackagedId)

    private var nextPauseRootContext: CommandContext<CommandSourceStack>? = null
    private var nextPauseSectionIndex: Int = 0

    private var lastPauseContext: CommandContext<CommandSourceStack>? = null
    private var lastPauseSourceIndex: Int = 0

    private val createdSourceReferences = Reference2IntOpenHashMap<EditorDebugConnection>()
    @Suppress("DEPRECATION")
    val currentSourceReference: Int?
        get() = createdSourceReferences[pauseContext.debugConnection!!]
    val currentSourceReferenceCursorMapper: ProcessedInputCursorMapper?
        get() = sourceReferenceCursorMapper[pauseContext.debugConnection!! to currentSourceReference]

    private var debugPauseHandler: DebugPauseHandler? = null
    override fun getDebugPauseHandler(): DebugPauseHandler {
        debugPauseHandler?.run { return this }
        val handler = debugPauseHandlerFactory.createDebugPauseHandler(this)
        debugPauseHandler = handler
        return handler
    }

    var breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>

    override fun onContinue(stackEntry: PauseContext.DebugFrameStack.Entry) {
        breakpoints = pauseContext.server.getDebugManager().functionDebugHandler.getFunctionBreakpoints(procedure.getOriginalId(), createdSourceReferences)
    }

    init {
        breakpoints = pauseContext.server.getDebugManager().functionDebugHandler.getFunctionBreakpoints(procedure.getOriginalId())
        if(breakpoints.isNotEmpty()) {
            // The debug pause handler might need to parse dynamic breakpoints
            getDebugPauseHandler()
        }
    }

    fun getBreakpointsForCommand(commandRootContext: CommandContext<CommandSourceStack>): List<ServerBreakpoint<FunctionBreakpointLocation>> {
        return breakpoints.filter { it.action?.location?.commandLocationRoot == commandRootContext }
    }

    fun checkPause(commandInfo: CommandInfo, context: CommandContext<*>, source: CommandSourceStack) {
        currentCommandIndex = commandInfo.commandIndex
        if(lastPauseContext === context && lastPauseSourceIndex == currentSectionSources.currentSourceIndex)
            return
        if(pauseContext.isDebugging()) {
            if(nextPauseRootContext === contextChains[commandInfo.commandIndex].topContext && nextPauseSectionIndex <= currentSectionIndex) {
                onReachedPauseLocation()
            }
            if(commandInfo.commandIndex > 0 && nextPauseRootContext === contextChains[commandInfo.commandIndex - 1].topContext) {
                nextPauseRootContext = null
                pauseContext.notifyClientPauseLocationSkipped()
                getDebugPauseHandler().findNextPauseLocation()
                checkPause(commandInfo, context, source)
                return
            }
        }
        for(breakpoint in commandInfo.breakpoints) {
            val action = breakpoint.action
            if(action != null && action.location.commandSectionLocation === context &&
                (action.condition == null || action.condition.checkCondition(source) && action.condition.checkHitCondition(
                    source
                ))
            ) {
                onBreakpointHit(breakpoint)
            }
        }
    }

    private var lastCommandInfoRequestedIndex = -1

    fun getCommandInfo(commandContext: CommandContext<CommandSourceStack>): CommandInfo? {
        val commands = contextChains.subList(currentCommandIndex, contextChains.size)
        for(i in commands.indices) {
            val topContext = commands[i].topContext
            var context: CommandContext<CommandSourceStack>? = topContext
            var sectionIndex = 0
            while(context != null) {
                if(context == commandContext) {
                    val commandIndex = i + currentCommandIndex
                    if(commandIndex != lastCommandInfoRequestedIndex) {
                        sectionSources.clear()
                        currentSectionIndex = 0
                        lastCommandInfoRequestedIndex = commandIndex
                    }
                    return CommandInfo(commandIndex, getBreakpointsForCommand(topContext), sectionIndex)
                }
                context = context.child
                sectionIndex++
            }
        }
        return null
    }

    fun pauseAtSection(rootContext: CommandContext<CommandSourceStack>, sectionIndex: Int) {
        nextPauseRootContext = rootContext
        nextPauseSectionIndex = sectionIndex
        pauseContext.unpause()
    }

    fun hasNextSection()
        = currentSectionIndex < (currentContextChain as ContextChainAccessor<*>).modifiers.size

    override fun unpause() {
        pauseContext.server.execute {
            pauseContext.executionWrapper.runCallback(unpauseCallback)
        }
    }

    override fun shouldWrapInSourceReference(path: String): Either<PauseContext.NewSourceReferenceWrapper, PauseContext.ExistingSourceReferenceWrapper>? {
        if(!path.endsWith(sourceFilePath)) return null
        val existingSourceReference = currentSourceReference
        if(existingSourceReference != INITIAL_SOURCE_REFERENCE)
            return Either.right(PauseContext.ExistingSourceReferenceWrapper(existingSourceReference, {
                it.path = sourceFilePath
                it.name += "@$existingSourceReference"
            }, false))
        val pauseHandler = getDebugPauseHandler()
        if(pauseHandler !is FileContentReplacer) return null
        val editorConnection = pauseContext.debugConnection ?: return null
        val replacementData = pauseHandler.getReplacementData(path)
        if(replacementData == null || !replacementData.replacements.iterator().hasNext()) return null
        return Either.left(PauseContext.NewSourceReferenceWrapper({
            createdSourceReferences[editorConnection] = it
            replacementData.sourceReferenceCallback(it)
        }) { sourceReference ->
            val newBreakpoints = pauseContext.server.getDebugManager().functionDebugHandler
                .getFunctionBreakpointsForDebugConnection(procedure.getOriginalId(), editorConnection).map {
                    PositionableBreakpoint(it.unparsed.copy())
                }
            val (replacedDocument, cursorMapper) = FileContentReplacer.Document(
                functionLines,
                newBreakpoints.asSequence() + replacementData.positionables
            ).applyReplacements(replacementData.replacements)
            sourceReferenceCursorMapper[editorConnection to sourceReference] = cursorMapper
            pauseContext.server.getDebugManager().functionDebugHandler.addNewSourceReferenceBreakpoints(
                newBreakpoints.map { BreakpointManager.NewSourceReferenceBreakpoint(it.breakpoint.sourceBreakpoint, it.breakpoint.id) },
                editorConnection,
                sourceFilePackagedIdWithoutExtension,
                sourceReference
            )
            replacedDocument.concatLines()
        })
    }

    override fun onExitFrame() {
        debugPauseHandler?.onExitFrame()
        val debugManager = pauseContext.server.getDebugManager()
        createdSourceReferences.forEach {
            debugManager.removeSourceReference(it.key, it.value)
            sourceReferenceCursorMapper.remove(it.key to it.value)
        }
        if(pauseContext.debugFrameDepth == 0 && pauseContext.oneTimeDebugConnection != null) {
            pauseContext.oneTimeDebugConnection.output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.IMPORTANT
                val commandResult = pauseContext.commandResult
                output = if(commandResult == null) {
                    "No return information available"
                } else {
                    val returnValue = commandResult.returnValue
                    if(returnValue == null) {
                        "Function didn't return a value"
                    } else {
                        "Function returned ${if(returnValue.first) "successfully" else "unsuccessfully"} with value ${returnValue.second}"
                    }
                }
            })
        }
    }
    
    private fun startPause() {
        lastPauseContext = currentContext
        lastPauseSourceIndex = currentSectionSources.currentSourceIndex
        nextPauseRootContext = null
        pauseContext.suspend() { CommandExecutionPausedThrowable(pauseContext.executionWrapper) }
    }

    fun resetLastPause() {
        lastPauseContext = null
    }

    fun onBreakpointHit(breakpoint: ServerBreakpoint<FunctionBreakpointLocation>) {
        if(breakpoint.unparsed.sourceReference == INITIAL_SOURCE_REFERENCE && breakpoint.debugConnection in createdSourceReferences.keys) {
            //This breakpoint shouldn't be paused at, because it doesn't belong to the debugee's sourceReference
            return
        }
        if(pauseContext.initBreakpointPause(breakpoint)) {
            startPause()
        }
    }

    fun onReachedPauseLocation() {
        if(pauseContext.initPauseLocationReached()) {
            startPause()
        }
    }

    class SectionSources(val sources: MutableList<CommandSourceStack>, val parentSourceIndices: MutableList<Int>, var currentSourceIndex: Int) {
        fun hasCurrent(): Boolean = currentSourceIndex < sources.size
        fun hasNext(): Boolean = currentSourceIndex < sources.size - 1
        fun getNext(): CommandSourceStack? = if(hasNext()) sources[currentSourceIndex + 1] else null

        var currentSource: CommandSourceStack
            get() = sources[currentSourceIndex]
            set(value) {
                sources[currentSourceIndex] = value
            }
    }

    class CommandInfo(val commandIndex: Int, val breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>, val sectionOffset: Int)

    override fun onCommandFeedback(feedback: String) {
        commandFeedbackConsumer?.onCommandFeedback(feedback)
    }

    override fun onCommandError(error: String) {
        commandFeedbackConsumer?.onCommandError(error)
    }
}