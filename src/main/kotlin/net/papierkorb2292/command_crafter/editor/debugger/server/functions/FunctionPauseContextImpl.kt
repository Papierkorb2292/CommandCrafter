package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.ParseResults
import net.minecraft.server.ServerTask
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.server.function.CommandFunctionManager
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.mixin.editor.debugger.CommandElementAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.CommandFunctionManagerAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.CommandFunctionManagerEntryAccessor
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class FunctionPauseContextImpl(
    override val executionQueue: Deque<CommandFunctionManager.Entry>,
    override var currentCommand: CommandFunction.CommandElement,
    val commandFunctionManager: CommandFunctionManager,
    val continueExecution: (FunctionPauseContextImpl) -> Unit
) : FunctionPauseContext {
    companion object {
        private const val STANDARD_VECTOR_INCREMENT = 100
        private fun <T> createStandardVector() = Vector<T>(STANDARD_VECTOR_INCREMENT, STANDARD_VECTOR_INCREMENT)
    }

    override val executedEntries: MutableList<CommandFunctionManager.Entry> = createStandardVector()
    override val contextStack: MutableList<FunctionPauseContext.SectionContexts> = createStandardVector()
    override var currentSectionIndex = 0
    override var indexOfCurrentSectionInContextStack = 0

    val functionCompletionFuture = CompletableFuture<Int>()
    var nextPauseLocation: PauseLocation? = null
        private set
    var dispatcherContext: DispatcherContext? = null
    var breakpointToSkip: ServerBreakpoint<FunctionBreakpointLocation>? = null

    var currentEditorConnection: EditorDebugConnection? = null
        private set

    private var pauseOnFunctionEntry = false
    private var pauseLocationWasSet = false
    private val debugStack: Deque<DebugStackElement> = LinkedList()

    private val variablesReferences = mutableListOf<VariablesReferencer>()

    private fun updateTopStackFrames() {
        val debuggerConnection = currentEditorConnection ?: return
        val currentStackElement = debugStack.peek() ?: return
        debuggerConnection.popStackFrames(currentStackElement.stackFrames)
        val stackFrames = currentStackElement.pauseHandler.getStackFrames()
        currentStackElement.stackFrames = stackFrames.size
        debuggerConnection.pushStackFrames(stackFrames)
    }

    override fun pauseAtCommandSection(command: ParseResults<ServerCommandSource>, sectionIndex: Int) {
        fun FunctionPauseContext.SectionContexts.hasExecutedNext(): Boolean {
            val dispatcherContext = dispatcherContext ?: return hasNext()
            return dispatcherContext.currentContextIndex > currentContextIndex
        }
        fun findNextPauseLocation() {
            runOnTick {
                pauseLocationWasSet = false
                debugStack.peek()?.pauseHandler?.findNextPauseLocation()
                if(!pauseLocationWasSet) {
                    continueExecution()
                }
            }
        }

        pauseLocationWasSet = true

        val indexOfSectionInContextStack = indexOfCurrentSectionInContextStack - currentSectionIndex + sectionIndex
        if (command == (currentCommand as CommandElementAccessor).parsed) {
            if(contextStack.size > indexOfSectionInContextStack + 1) {
                val sectionContexts = contextStack[indexOfSectionInContextStack]
                if(!sectionContexts.hasNext()) {
                    findNextPauseLocation()
                    return
                }
                sectionContexts.currentContextIndex++
                runOnTick { pauseAtPreviouslyExecutedSection(sectionIndex) }
                return
            }
            if (contextStack.size > indexOfSectionInContextStack) {
                val sectionContexts = contextStack[indexOfSectionInContextStack]
                // The target pause location is in the section that was previously paused in
                if(sectionContexts.hasExecutedNext()) {
                    sectionContexts.currentContextIndex++
                    runOnTick { pauseAtPreviouslyExecutedSection(sectionIndex) }
                    return
                }
                nextPauseLocation = PauseLocation(
                    command,
                    sectionIndex,
                    indexOfSectionInContextStack == indexOfCurrentSectionInContextStack
                )
                continueExecution()
                return
            }
        }
        nextPauseLocation = PauseLocation(
            command,
            sectionIndex,
        )
        continueExecution()
    }

    private fun pauseAtPreviouslyExecutedSection(sectionIndex: Int) {
        val debugPauseHandler = (debugStack.peek() ?: return).pauseHandler
        val indexOfSectionInContextStack = indexOfCurrentSectionInContextStack - currentSectionIndex + sectionIndex

        val targetSectionContexts = contextStack[indexOfSectionInContextStack]

        indexOfCurrentSectionInContextStack = indexOfSectionInContextStack
        currentSectionIndex = sectionIndex

        // If the debugPauseHandler doesn't stop at any of the targeted contexts,
        // the section is considered skipped
        pauseLocationWasSet = false
        while(!debugPauseHandler.shouldStopOnCurrentContext()) {
            if(pauseLocationWasSet) {
                return
            }
            setPreviousCurrentContextsIndices(sectionIndex)
            if (targetSectionContexts.advancedExists()) continue
            debugPauseHandler.findNextPauseLocation()
            if(!pauseLocationWasSet) {
                continueExecution()
            }
            return
        }
        setPreviousCurrentContextsIndices(sectionIndex)
        updateTopStackFrames()
        currentEditorConnection?.pauseStarted(debugPauseHandler, StoppedEventArguments().apply {
            reason = StoppedEventArgumentsReason.STEP
            threadId = 0
        }, this)
        return
    }

    override fun stepIntoFunctionCall() {
        pauseOnFunctionEntry = true
        pauseLocationWasSet = true
        continueExecution()
    }

    override fun stepOutOfFunction() {
        pauseLocationWasSet = true
        continueExecution()
    }

    override fun continue_() {
        removeEditorConnection()
        continueExecution()
    }

    private fun continueExecution() {
        if(nextPauseLocation == null && !pauseOnFunctionEntry && debugStack.size <= 1) {
            removeEditorConnection()
        } else {
            currentEditorConnection?.pauseEnded()
        }
        variablesReferences.clear()
        runOnTick { continueExecution(this) }
    }

    private fun removeEditorConnection() {
        currentEditorConnection?.run {
            pauseEnded()
            popStackFrames(debugStack.sumOf { it.stackFrames })
        }
        currentEditorConnection = null
    }

    override fun addVariablesReferencer(referencer: VariablesReferencer): Int {
        val id = variablesReferences.size + 1
        variablesReferences.add(referencer)
        return id
    }

    private fun runOnTick(action: Runnable) {
        val server = (commandFunctionManager as CommandFunctionManagerAccessor).server
        server.send(ServerTask(server.ticks, action))
    }

    fun onFunctionEnter(function: CommandFunction) {
        @Suppress("UNCHECKED_CAST")
        val debugInformation = (function as DebugInformationContainer<FunctionBreakpointLocation, FunctionPauseContext>)
            .`command_crafter$getDebugInformation`() ?: return
        debugStack.push(DebugStackElement(debugInformation.createDebugPauseHandler(this)))
    }

    @Throws(ExecutionPausedThrowable::class)
    fun onFunctionDepthIncreased() {
        currentEditorConnection?.let {
            if(pauseOnFunctionEntry) {
                pauseOnFunctionEntry = false
                val lastElement = (executedEntries.last() as CommandFunctionManagerEntryAccessor).element
                if(lastElement is CommandElementAccessor) {
                    nextPauseLocation = PauseLocation(lastElement.parsed, 0)
                }
            }
        }
    }

    @Throws(ExecutionPausedThrowable::class)
    fun onFunctionExit() {
        val poppedStackElement = debugStack.poll()
        if(poppedStackElement != null) {
            currentEditorConnection?.popStackFrames(poppedStackElement.stackFrames)
        }
        if(currentEditorConnection != null) {
            debugStack.peek()?.run {
                pauseLocationWasSet = false
                pauseHandler.findNextPauseLocation()
                if (!pauseLocationWasSet) {
                    if (debugStack.size <= 1) removeEditorConnection()
                } else {
                    executedEntries += executionQueue.poll()
                    throw ExecutionPausedThrowable(functionCompletionFuture)
                }
            }
        }
    }

    private fun applyAdditionalContextStack(dispatcherContext: DispatcherContext) {
        this.dispatcherContext = dispatcherContext
        contextStack[indexOfCurrentSectionInContextStack].currentContextIndex =
            dispatcherContext.currentContextIndex
    }

    @Throws(ExecutionPausedThrowable::class)
    fun onBreakpointHit(breakpoint: ServerBreakpoint<FunctionBreakpointLocation>, dispatcherContext: DispatcherContext) {
        applyAdditionalContextStack(dispatcherContext)
        setPreviousCurrentContextsIndices(currentSectionIndex)

        currentEditorConnection = breakpoint.debuggerConnection
        val pauseHandler = (debugStack.peek() ?: return).pauseHandler
        for(debugElement in debugStack) {
            val stackFrames = debugElement.pauseHandler.getStackFrames()
            debugElement.stackFrames = stackFrames.size
            breakpoint.debuggerConnection.pushStackFrames(stackFrames)
        }
        pauseExecution(breakpoint.debuggerConnection, pauseHandler, StoppedEventArguments().apply {
            reason = StoppedEventArgumentsReason.BREAKPOINT
            hitBreakpointIds = arrayOf(breakpoint.unparsed.id)
        })
    }

    @Throws(ExecutionPausedThrowable::class)
    fun onPauseLocationReached(dispatcherContext: DispatcherContext) {
        applyAdditionalContextStack(dispatcherContext)

        val debugPauseHandler = (debugStack.peek() ?: return).pauseHandler

        nextPauseLocation = null
        pauseLocationWasSet = false
        if(!debugPauseHandler.shouldStopOnCurrentContext()) {
            if(pauseLocationWasSet)
                throw ExecutionPausedThrowable(functionCompletionFuture)
            setPreviousCurrentContextsIndices(currentSectionIndex)
            if(debugStack.size <= 1) {
                removeEditorConnection()
            }
            return
        }
        setPreviousCurrentContextsIndices(currentSectionIndex)
        updateTopStackFrames()

        currentEditorConnection?.let {
            pauseExecution(it, debugPauseHandler, StoppedEventArguments().apply {
                reason = StoppedEventArgumentsReason.STEP
            })
        }
    }

    fun skippedPauseLocation(dispatcherContext: DispatcherContext) {
        debugStack.peek()?.let {
            pauseLocationWasSet = false
            it.pauseHandler.findNextPauseLocation()
            if(pauseLocationWasSet) {
                applyAdditionalContextStack(dispatcherContext)
                throw ExecutionPausedThrowable(functionCompletionFuture)
            }
            if(debugStack.size <= 1) {
                removeEditorConnection()
            }
        }
    }

    private fun setPreviousCurrentContextsIndices(currentSectionIndex: Int) {
        if(currentSectionIndex == 0) return
        val currentSectionContexts = contextStack[currentSectionIndex]
        var contextGroupIndex = currentSectionContexts.getGroupIndexOfContext(currentSectionContexts.currentContextIndex)
        for(sectionContexts in contextStack.subList(indexOfCurrentSectionInContextStack - this.currentSectionIndex, currentSectionIndex).asReversed()) {
            val newContextIndex = contextGroupIndex ?: sectionContexts.contexts.size
            sectionContexts.currentContextIndex = newContextIndex
            contextGroupIndex = sectionContexts.getGroupIndexOfContext(newContextIndex)
        }
    }

    @Throws(ExecutionPausedThrowable::class)
    private fun pauseExecution(debugConnection: EditorDebugConnection, pauseHandler: DebugPauseHandler, stoppedArgs: StoppedEventArguments): Nothing {
        stoppedArgs.threadId = 0
        debugConnection.pauseStarted(pauseHandler, stoppedArgs, this)
        throw ExecutionPausedThrowable(functionCompletionFuture)
    }

    class PauseLocation(val command: ParseResults<ServerCommandSource>, val sectionIndex: Int, val pauseAfterExecution: Boolean = false)

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        val variablesReferenceIndex = args.variablesReference - 1
        val variablesReferences = variablesReferences
        if(variablesReferenceIndex >= variablesReferences.size)
            return CompletableFuture.completedFuture(emptyArray())
        return variablesReferences[variablesReferenceIndex].getVariables(args)
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val variablesReferenceIndex = args.variablesReference - 1
        val variablesReferences = variablesReferences
        if(variablesReferenceIndex >= variablesReferences.size)
            return CompletableFuture.completedFuture(null)
        return variablesReferences[variablesReferenceIndex].setVariable(args)
    }
}