package net.papierkorb2292.command_crafter.editor.debugger.server

import it.unimi.dsi.fastutil.objects.Reference2IntMap
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugHandler
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferenceMapper
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class PauseContext(val server: MinecraftServer) {
    companion object {
        val currentPauseContext = ThreadLocal<PauseContext>()

        fun trySetUpPauseContext(supplier: () -> PauseContext): Boolean {
            if(currentPauseContext.get() != null) {
                return false
            }
            currentPauseContext.set(supplier())
            return true
        }

        fun resetPauseContext() {
            currentPauseContext.remove()
        }

        fun wrapExecution(executionPausedThrowable: ExecutionPausedThrowable) {
            val pauseContext = currentPauseContext.get() ?: return
            executionPausedThrowable.wrapperConsumer.accept(object : ExecutionWrapper {
                override fun runWrapped(inner: () -> Unit) {
                    currentPauseContext.set(pauseContext)
                    inner.invoke()
                    resetPauseContext()
                }
            })
            resetPauseContext()
        }
    }

    val stepInTargetsManager = StepInTargetsManager()

    val variablesReferenceMapper = object : VariablesReferenceMapper {
        private val variablesReferences = mutableListOf<VariablesReferencer>()

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
        override fun addVariablesReferencer(referencer: VariablesReferencer): Int {
            val id = variablesReferences.size + 1
            variablesReferences.add(referencer)
            return id
        }
    }



    private val currentDebugPauseHandler: DebugPauseHandler?
        get() = debugFrameStack.peek()?.frame?.getDebugPauseHandler()

    private val debugPauseActionsWrapper = object : DebugPauseActions {
        override fun next(granularity: SteppingGranularity) {
            currentDebugPauseHandler?.next(granularity)
        }
        override fun stepOut(granularity: SteppingGranularity) {
            currentDebugPauseHandler?.stepOut(granularity)
        }
        override fun continue_() {
            currentDebugPauseHandler?.continue_()
        }

        override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
            if(targetId == null)
                currentDebugPauseHandler?.stepIn(granularity)
            else
                stepInTargetsManager.stepIn(targetId)
        }
        override fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse> {
            debugFrameStack.getEntryForMinecraftStackFrame(frameId)?.let { (entry, minecraftStackFrameIndex) ->
                return entry.frame.getDebugPauseHandler().stepInTargets(minecraftStackFrameIndex)
            }
            return CompletableFuture.completedFuture(StepInTargetsResponse())
        }
    }

    val executionWrapper = ExecutionWrapperConsumerImpl(server)

    private val debugFrameStack = DebugFrameStack()

    var debugConnection: EditorDebugConnection? = null
        private set
    var isPaused: Boolean = false
        private set

    private var pauseOnFrameEnter = false
    private var pauseOnFrameExit: Int? = null

    fun isDebugging(): Boolean {
        return debugConnection != null
    }

    fun pushDebugFrame(frame: DebugFrame) {
        debugFrameStack.push(frame)
        if(pauseOnFrameEnter) {
            pauseOnFrameEnter = false
            frame.getDebugPauseHandler().findNextPauseLocation()
        }
    }
    fun popDebugFrame() {
        val entry = debugFrameStack.pop()
        entry.minecraftStackFrameCount?.let { debugConnection?.popStackFrames(it) }
        val debugManager = server.getDebugManager()
        entry.createdSourceReferences.reference2IntEntrySet().forEach { (networkHandler, sourceReferenceId) ->
            debugManager.removeSourceReference(networkHandler, sourceReferenceId)
        }
        if(debugConnection != null && entry.everPaused) {
            if(pauseOnFrameExit == null) {
                notifyClientPauseLocationSkipped()
                currentDebugPauseHandler?.findNextPauseLocation()
                return
            }
            if(pauseOnFrameExit == debugFrameStack.stack.size + 1) {
                pauseOnFrameExit = null
                currentDebugPauseHandler?.findNextPauseLocation()
            }
        }
    }
    fun peekDebugFrame() =
        debugFrameStack.peek()?.frame

    fun pauseAfterExitFrame() {
        pauseOnFrameExit = debugFrameStack.stack.size
        unpause()
    }
    fun stepIntoFrame() {
        pauseOnFrameEnter = true
        unpause()
    }
    fun removePause() {
        debugConnection?.run {
            pauseEnded()
            for(entry in debugFrameStack.stack) {
                entry.minecraftStackFrameCount?.let {
                    popStackFrames(it)
                }
                entry.minecraftStackFrameCount  = null
            }
            debugConnection = null
        }
        unpause()
    }
    fun unpause() {
        if(!isPaused)
            return
        isPaused = false
        debugConnection?.pauseEnded()
        debugFrameStack.stack.forEach { it.frame.onContinue(it) }
        onContinueListeners.forEach { it() }
        peekDebugFrame()?.unpause()
    }

    fun notifyClientPauseLocationSkipped() {
        debugConnection?.onPauseLocationSkipped()
    }

    fun initBreakpointPause(breakpoint: ServerBreakpoint<*>): Boolean {
        var debugConnection = debugConnection
        if(debugConnection != null) {
            if(breakpoint.debugConnection != debugConnection) {
                //A different debugee is currently debugging the function
                return false
            }
            val currentDebugFrame = debugFrameStack.peek()
            if(currentDebugFrame != null && breakpoint.unparsed.sourceReference == null && breakpoint.debugConnection in currentDebugFrame.createdSourceReferences.keys) {
                //This breakpoint shouldn't be paused at, because it doesn't belong to the debugee's sourceReference
                return false
            }
        } else {
            debugConnection = breakpoint.debugConnection
            this.debugConnection = debugConnection
        }
        debugFrameStack.peek()!!.everPaused = true
        isPaused = true
        updateStackFrames(debugConnection)
        debugConnection.pauseStarted(debugPauseActionsWrapper, StoppedEventArguments().also {
            it.hitBreakpointIds = arrayOf(breakpoint.unparsed.id)
            it.reason = StoppedEventArgumentsReason.BREAKPOINT
        }, variablesReferenceMapper)
        return true
    }

    fun initPauseLocationReached(): Boolean {
        if(currentDebugPauseHandler?.shouldStopOnCurrentContext() == false)
            return false
        debugConnection?.let { connection ->
            debugFrameStack.peek()!!.everPaused = true
            isPaused = true
            updateStackFrames(connection)
            connection.pauseStarted(debugPauseActionsWrapper, StoppedEventArguments().also {
                it.reason = StoppedEventArgumentsReason.PAUSE
            }, variablesReferenceMapper)
            return true
        }
        return false
    }

    private val onContinueListeners = mutableListOf<() -> Unit>()

    fun addOnContinueListener(callback: () -> Unit) {
        //TODO: Called multiple times
        onContinueListeners += callback
    }
    fun removeOnContinueListener(callback: () -> Unit) {
        onContinueListeners -= callback
    }

    private fun updateStackFrames(debugConnection: EditorDebugConnection) {
        val lastEntry = debugFrameStack.peek() ?: return
        lastEntry.minecraftStackFrameCount?.let {
            debugConnection.popStackFrames(it)
            debugConnection.pushStackFrames(getSourceReferenceWrappedStackFrames(lastEntry, debugConnection))
            return
        }

        for(entry in debugFrameStack.stack) {
            if(entry.minecraftStackFrameCount == null) {
                debugConnection.pushStackFrames(getSourceReferenceWrappedStackFrames(entry, debugConnection))
            }
        }
    }

    private fun getSourceReferenceWrappedStackFrames(debugStackEntry: DebugFrameStack.Entry, debugConnection: EditorDebugConnection): List<MinecraftStackFrame> {
        fun wrapStackFrameWithSourceReference(stackFrame: MinecraftStackFrame, sourceReferenceId: Int): MinecraftStackFrame {
            val source = stackFrame.visualContext.source
            return MinecraftStackFrame(
                stackFrame.name,
                DebuggerVisualContext(Source().apply {
                    name = FunctionDebugHandler.getSourceName(source.path, sourceReferenceId)
                    path = source.path
                    sourceReference = sourceReferenceId
                    presentationHint = source.presentationHint
                    origin = source.origin
                    sources = arrayOf(source)
                }, stackFrame.visualContext.range),
                stackFrame.variableScopes,
                stackFrame.presentationHint
            )
        }

        @Suppress("DEPRECATION") // createdSourceReferences.get is supposed to be nullable
        val sourceReference = debugStackEntry.createdSourceReferences[debugConnection]
        val stackFrames = debugStackEntry.frame.getDebugPauseHandler().getStackFrames(sourceReference)
        debugStackEntry.minecraftStackFrameCount = stackFrames.size
        val wrappedStackFrames = mutableListOf<MinecraftStackFrame>()
        for(stackFrame in stackFrames) {
            val source = stackFrame.visualContext.source
            val sourcePath = source.path
            val pathSourceReferences = debugStackEntry.createdSourceReferences
            if(pathSourceReferences.containsKey(debugConnection)) {
                val sourceReferenceId = pathSourceReferences.getInt(debugConnection)
                wrappedStackFrames.add(wrapStackFrameWithSourceReference(stackFrame, sourceReferenceId))
                continue
            }
            val sourceReferenceWrapper = debugStackEntry.frame.shouldWrapInSourceReference(sourcePath)
            if (sourceReferenceWrapper == null) {
                wrappedStackFrames.add(stackFrame)
                continue
            }
            val sourceReferenceId = server.getDebugManager().addSourceReference(debugConnection) {
                SourceResponse().apply {
                    content = sourceReferenceWrapper.content(it)
                }
            }
            debugStackEntry.createdSourceReferences.put(debugConnection, sourceReferenceId)
            sourceReferenceWrapper.sourceReferenceCallback(sourceReferenceId)
            wrappedStackFrames.add(wrapStackFrameWithSourceReference(stackFrame, sourceReferenceId))
        }
        return wrappedStackFrames
    }

    interface DebugFrame {
        fun getDebugPauseHandler(): DebugPauseHandler
        fun unpause()
        fun shouldWrapInSourceReference(path: String): SourceReferenceWrapper?
        fun onExitFrame()
        fun onContinue(stackEntry: DebugFrameStack.Entry)
    }

    class SourceReferenceWrapper(val sourceReferenceCallback: (Int) -> Unit, val content: (Int) -> String)

    class DebugFrameStack {
        val stack = LinkedList<Entry>()

        fun push(frame: DebugFrame) {
            stack.addLast(Entry(frame))
        }
        fun peek(): Entry? = stack.peekLast()
        fun pop(): Entry = stack.removeLast().apply { frame.onExitFrame() }

        fun getEntryForMinecraftStackFrame(minecraftStackFrameIndex: Int): Pair<Entry, Int>? {
            var minecraftStackFrameCount = minecraftStackFrameIndex
            for(entry in stack) {
                val entryStackFrameCount = entry.minecraftStackFrameCount ?: continue
                if(minecraftStackFrameCount < entryStackFrameCount)
                    return entry to minecraftStackFrameCount
                minecraftStackFrameCount -= entryStackFrameCount
            }
            return null
        }

        class Entry(
            val frame: DebugFrame,
            var minecraftStackFrameCount: Int? = null,
            var createdSourceReferences: Reference2IntMap<EditorDebugConnection> = Reference2IntOpenHashMap(),
            var everPaused: Boolean = false,
        )
    }

    /**
     * Lets you wrap the execution after a pause continues with
     * a 'before' and an 'after' callback.
     *
     * Implementations should make sure that these callbacks
     * persist between pauses, because usually the original execution
     * initiator only gets this wrapper once at the first pause, since
     * after that the execution is resumed by another caller.
     */
    interface ExecutionWrapperConsumer {
        fun accept(wrapper: ExecutionWrapper)
        fun remove(wrapper: ExecutionWrapper)
    }

    interface ExecutionWrapper {
        fun runWrapped(inner: () -> Unit)
    }

    class ExecutionWrapperConsumerImpl(val server: MinecraftServer) : ExecutionWrapperConsumer {
        private val wrappers = mutableListOf<ExecutionWrapper>()

        override fun accept(wrapper: ExecutionWrapper) {
            wrappers.add(0, wrapper)
        }

        override fun remove(wrapper: ExecutionWrapper) {
            wrappers.remove(wrapper)
        }

        fun runCallback(callback: () -> Unit) {
            server.send(ServerTask(server.ticks, wrappers.fold(callback) { acc, wrapper -> { wrapper.runWrapped(acc) } }))
        }
    }
}