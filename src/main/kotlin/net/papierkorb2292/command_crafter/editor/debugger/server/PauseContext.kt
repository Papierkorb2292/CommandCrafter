package net.papierkorb2292.command_crafter.editor.debugger.server

import it.unimi.dsi.fastutil.objects.Reference2IntMap
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebuggerVisualContext
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.helper.ServerDebugManagerContainer
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugHandler
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferenceMapper
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class PauseContext(val server: MinecraftServer) {
    companion object {
        val currentPauseContext = ThreadLocal<PauseContext?>()

        fun trySetUpPauseContext(supplier: () -> PauseContext): Boolean {
            if(currentPauseContext.get() != null) {
                return false
            }
            currentPauseContext.set(supplier())
            return true
        }

        fun resetPauseContext() {
            currentPauseContext.set(null)
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

    val executionWrapper = ExecutionWrapperConsumerImpl(server)

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

    private val debugFrameStack = DebugFrameStack()

    var editorConnection: ServerNetworkDebugConnection? = null
        private set

    private var pauseOnFrameEnter = false
    private var pauseOnFrameExit = false

    fun isDebugging(): Boolean {
        return editorConnection != null
    }

    fun pushDebugFrame(frame: DebugFrame) {
        debugFrameStack.push(frame)
        editorConnection?.run {
            pushStackFrames(getSourceReferenceWrappedStackFrames(debugFrameStack.peek()!!, player))
        }
        if(pauseOnFrameEnter) {
            pauseOnFrameEnter = false
            frame.getDebugPauseHandler().findNextPauseLocation()
            //TODO
        }
    }
    fun popDebugFrame() {
        val entry = debugFrameStack.pop()
        editorConnection?.popStackFrames(entry.minecraftStackFrameCount)
        val debugManager = (server as ServerDebugManagerContainer).`command_crafter$getServerDebugManager`()
        entry.createdSourceReferences.reference2IntEntrySet().forEach { (networkHandler, sourceReferenceId) ->
            debugManager.removeSourceReference(networkHandler, sourceReferenceId)
        }
        if(pauseOnFrameExit) {
            pauseOnFrameExit = false
            currentDebugPauseHandler?.findNextPauseLocation()
            //TODO
        }
    }
    fun peekDebugFrame() =
        debugFrameStack.peek()?.frame

    fun pauseAfterExitFrame() {
        pauseOnFrameExit = true
        unpause()
    }
    fun stepIntoFrame() {
        pauseOnFrameEnter = true
        unpause()
    }
    fun removePause() {
        editorConnection?.let {
            it.pauseEnded()
            for(entry in debugFrameStack.stack)
                it.popStackFrames(entry.minecraftStackFrameCount)
            editorConnection = null
        }
        unpause()
    }
    fun unpause() {
        editorConnection?.pauseEnded()
        onContinueListeners.forEach { it() }
        peekDebugFrame()?.unpause()
    }

    fun notifyClientPauseLocationSkipped() {
        editorConnection?.onPauseLocationSkipped()
    }

    fun initBreakpointPause(breakpoint: ServerBreakpoint<*>): Boolean {
        var editorConnection = editorConnection
        if(editorConnection != null) {
            if(breakpoint.editorConnection != editorConnection) {
                //A different player is currently debugging the function
                return false
            }
            val currentDebugFrame = debugFrameStack.peek()
            if(currentDebugFrame != null && breakpoint.unparsed.sourceReference == null && breakpoint.editorConnection.player.networkHandler in currentDebugFrame.createdSourceReferences.keys) {
                //This breakpoint shouldn't be paused at, because it doesn't belong to the player's sourceReference
                return false
            }
        } else {
            editorConnection = breakpoint.editorConnection
            initEditorConnection(editorConnection)
        }
        editorConnection.pauseStarted(debugPauseActionsWrapper, StoppedEventArguments().also {
            it.hitBreakpointIds = arrayOf(breakpoint.unparsed.id)
            it.reason = StoppedEventArgumentsReason.BREAKPOINT
        }, variablesReferenceMapper)

        return true
    }

    fun initPauseLocationReached(): Boolean {
        editorConnection?.let { connection ->
            connection.pauseStarted(debugPauseActionsWrapper, StoppedEventArguments().also {
                it.reason = StoppedEventArgumentsReason.PAUSE
            }, variablesReferenceMapper)
            return true
        }
        return false
    }

    private val onContinueListeners = mutableListOf<() -> Unit>()

    fun addOnContinueListener(callback: () -> Unit) {
        onContinueListeners += callback
    }
    fun removeOnContinueListener(callback: () -> Unit) {
        onContinueListeners -= callback
    }

    private fun initEditorConnection(editorConnection: ServerNetworkDebugConnection) {
        this.editorConnection = editorConnection
        for(entry in debugFrameStack.stack) {
            editorConnection.pushStackFrames(getSourceReferenceWrappedStackFrames(entry, editorConnection.player))
        }
    }

    private fun getSourceReferenceWrappedStackFrames(debugStackEntry: DebugFrameStack.Entry, player: ServerPlayerEntity): List<MinecraftStackFrame> {
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

        val stackFrames = debugStackEntry.frame.getDebugPauseHandler().getStackFrames(debugStackEntry.createdSourceReferences.getInt(player.networkHandler))
        debugStackEntry.minecraftStackFrameCount = stackFrames.size
        val wrappedStackFrames = mutableListOf<MinecraftStackFrame>()
        for(stackFrame in stackFrames) {
            val source = stackFrame.visualContext.source
            val sourcePath = source.path
            val pathSourceReferences = debugStackEntry.createdSourceReferences
            if(pathSourceReferences.containsKey(player.networkHandler)) {
                val sourceReferenceId = pathSourceReferences.getInt(player.networkHandler)
                wrappedStackFrames.add(wrapStackFrameWithSourceReference(stackFrame, sourceReferenceId))
                continue
            }
            val sourceReferenceWrapper = debugStackEntry.frame.shouldWrapInSourceReference(sourcePath)
            if (sourceReferenceWrapper == null) {
                wrappedStackFrames.add(stackFrame)
                continue
            }
            val sourceReferenceId = (server as ServerDebugManagerContainer).`command_crafter$getServerDebugManager`().addSourceReference(player) {
                SourceResponse().apply {
                    content = sourceReferenceWrapper.content(it)
                }
            }
            debugStackEntry.createdSourceReferences.put(player.networkHandler, sourceReferenceId)
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
    }

    class SourceReferenceWrapper(val sourceReferenceCallback: (Int) -> Unit, val content: (Int) -> String)

    class DebugFrameStack {
        val stack = LinkedList<Entry>()

        fun push(frame: DebugFrame) {
            stack.addLast(Entry(frame, 0))
        }
        fun peek(): Entry? = stack.peekLast()
        fun pop(): Entry = stack.removeLast().apply { frame.onExitFrame() }

        fun getEntryForMinecraftStackFrame(minecraftStackFrameIndex: Int): Pair<Entry, Int>? {
            var minecraftStackFrameCount = minecraftStackFrameIndex
            for(entry in stack) {
                if(minecraftStackFrameCount < entry.minecraftStackFrameCount)
                    return entry to minecraftStackFrameCount
                minecraftStackFrameCount -= entry.minecraftStackFrameCount
            }
            return null
        }

        class Entry(val frame: DebugFrame, var minecraftStackFrameCount: Int, var createdSourceReferences: Reference2IntMap<ServerPlayNetworkHandler> = Reference2IntOpenHashMap())
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
    }

    interface ExecutionWrapper {
        fun runWrapped(inner: () -> Unit)
    }

    class ExecutionWrapperConsumerImpl(val server: MinecraftServer) : ExecutionWrapperConsumer {
        private val wrappers = mutableListOf<ExecutionWrapper>()

        override fun accept(wrapper: ExecutionWrapper) {
            wrappers.add(0, wrapper)
        }

        fun runCallback(callback: () -> Unit) {
            server.send(ServerTask(server.ticks, wrappers.fold(callback) { acc, wrapper -> { wrapper.runWrapped(acc) } }))
        }

        fun addTo(wrapperConsumer: ExecutionWrapperConsumer) {
            for(wrapper in wrappers)
                wrapperConsumer.accept(wrapper)
        }
    }
}