package net.papierkorb2292.command_crafter.editor.debugger.server

import com.mojang.datafixers.util.Either
import net.minecraft.network.packet.s2c.play.UpdateTickRateS2CPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.util.Util
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferenceMapper
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem
import net.papierkorb2292.command_crafter.mixin.MinecraftServerAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ServerCommonNetworkHandlerAccessor
import org.eclipse.lsp4j.debug.*
import java.lang.Thread
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class PauseContext(val server: MinecraftServer, val oneTimeDebugConnection: EditorDebugConnection?, val pauseOnEntry: Boolean = false) {
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
                    try {
                        inner.invoke()
                    } finally {
                        resetPauseContext()
                    }
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

    var debugConnection: EditorDebugConnection? = oneTimeDebugConnection
        private set
    var isPaused: Boolean = false
        private set
    val debugFrameDepth get() = debugFrameStack.stack.size

    private var pauseOnFrameEnter = false
    private var pauseOnFrameExit: Int? = null
    private var suspendedServer = false

    fun isDebugging(): Boolean {
        return debugConnection != null
    }

    fun pushDebugFrame(frame: DebugFrame) {
        debugFrameStack.push(frame)
        if(pauseOnFrameEnter || (pauseOnEntry && debugFrameStack.stack.size == 1)) {
            pauseOnFrameEnter = false
            frame.getDebugPauseHandler().findNextPauseLocation()
        }
    }
    fun popDebugFrame() {
        val entry = debugFrameStack.pop()
        entry.minecraftStackFrameCount?.let { debugConnection?.popStackFrames(it) }
        if(debugConnection != null && entry.everPaused) {
            if(pauseOnFrameExit == null) {
                notifyClientPauseLocationSkipped()
                currentDebugPauseHandler?.findNextPauseLocation()
            } else if(pauseOnFrameExit == debugFrameStack.stack.size + 1) {
                pauseOnFrameExit = null
                currentDebugPauseHandler?.findNextPauseLocation()
            }
        }
        if(debugFrameStack.stack.isEmpty() && oneTimeDebugConnection != null) {
            oneTimeDebugConnection.lifecycle.shouldExitEvent.complete(ExitedEventArguments())
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
        if(!suspendedServer)
            peekDebugFrame()?.unpause()
    }

    fun notifyClientPauseLocationSkipped() {
        debugConnection?.onPauseLocationSkipped()
    }

    fun initBreakpointPause(breakpoint: ServerBreakpoint<*>): Boolean {
        var debugConnection = debugConnection ?: oneTimeDebugConnection
        if(breakpoint.debugConnection.isPaused()) {
            //The debuggee already paused somewhere
            return false
        }
        if(debugConnection != null) {
            if(breakpoint.debugConnection != debugConnection) {
                //A different debugee is currently debugging the function
                return false
            }
        } else if(breakpoint.debugConnection.oneTimeDebugTarget != null) {
            // The breakpoint comes from a one time debugee
            return false
        } else {
            debugConnection = breakpoint.debugConnection
        }
        this.debugConnection = debugConnection
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
        val connection = debugConnection ?: return false
        debugFrameStack.peek()!!.everPaused = true
        isPaused = true
        updateStackFrames(connection)
        connection.pauseStarted(debugPauseActionsWrapper, StoppedEventArguments().also {
            it.reason = StoppedEventArgumentsReason.PAUSE
        }, variablesReferenceMapper)
        return true
    }

    fun suspend(executionPausedThrowable: Supplier<Throwable>) {
        if(!debugConnection!!.suspendServer)
            throw executionPausedThrowable.get()
        suspendedServer = true
        val tickDelayMs = 50
        var lastTickMs = Util.getMeasuringTimeMs()

        // Flushing might be disabled, which would cause timeouts
        // (for example when the world is being ticked at the moment, which is
        // the case if for example the commands are run by a command block)

        val flushDisabledNetworkHandlers = server.playerManager.playerList.map {
            it.networkHandler
        }.filter {
            (it as ServerCommonNetworkHandlerAccessor).flushDisabled
        }

        for(flushDisabledNetworkHandler in flushDisabledNetworkHandlers)
            flushDisabledNetworkHandler.enableFlush()

        // Tell clients that the server is frozen so they don't really continue ticking
        val serverAlreadyFrozen = server.tickManager.isFrozen
        if(!serverAlreadyFrozen)
            server.playerManager.sendToAll(UpdateTickRateS2CPacket(server.tickManager.tickRate, true))

        while(isPaused) {
            val sleepDurationMs = lastTickMs + tickDelayMs - Util.getMeasuringTimeMs()
            if(sleepDurationMs > 0)
                Thread.sleep(sleepDurationMs)
            lastTickMs = Util.getMeasuringTimeMs()
            // Prevent watchdog from killing the server due to a too long tick
            (server as MinecraftServerAccessor).setTickStartTimeNanos(Util.getMeasuringTimeNano())
            // Tick network handlers to keep connections alive
            // Copy list in case 'callBaseTick' disconnects a player, which would modify the player list
        for(player in server.playerManager.playerList.toList())
                (player.networkHandler as ServerCommonNetworkHandlerAccessor).callBaseTick()
            ServerScoreboardStorageFileSystem.runUpdates()
        }

        for(flushDisabledNetworkHandler in flushDisabledNetworkHandlers)
            flushDisabledNetworkHandler.disableFlush()

        if(!serverAlreadyFrozen)
            server.playerManager.sendToAll(UpdateTickRateS2CPacket(server.tickManager.tickRate, false))

        suspendedServer = false
    }

    private val onContinueListeners = mutableListOf<() -> Unit>()

    fun addOnContinueListener(callback: () -> Unit) {
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
        fun wrapStackFrameWithSourceReference(stackFrame: MinecraftStackFrame, sourceReferenceId: Int?): MinecraftStackFrame {
            val source = stackFrame.visualContext.source
            return MinecraftStackFrame(
                stackFrame.name,
                DebuggerVisualContext(Source().apply {
                    name = source.name
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

        val stackFrames = debugStackEntry.frame.getDebugPauseHandler().getStackFrames()
        debugStackEntry.minecraftStackFrameCount = stackFrames.size
        val wrappedStackFrames = mutableListOf<MinecraftStackFrame>()
        for(stackFrame in stackFrames) {
            val source = stackFrame.visualContext.source
            if(source.sourceReference != null && source.sourceReference != 0) {
                wrappedStackFrames.add(stackFrame)
                continue
            }
            val sourcePath = source.path
            val sourceReferenceWrapper = debugStackEntry.frame.shouldWrapInSourceReference(sourcePath)
            if(sourceReferenceWrapper == null) {
                wrappedStackFrames.add(stackFrame)
                continue
            }
            val updateStackFrames = sourceReferenceWrapper.map(
                {
                    val sourceReferenceId = server.getDebugManager()
                        .addSourceReference(debugConnection) { sourceReference ->
                            val content = it.content(sourceReference)
                            SourceResponse().apply {
                                this.content = content
                            }
                        }
                    it.sourceReferenceCallback(sourceReferenceId)
                    true
                },
                {
                    val newFrame = wrapStackFrameWithSourceReference(stackFrame, it.sourceReferenceId)
                    it.additionalSourceChanges(newFrame.visualContext.source)
                    wrappedStackFrames.add(newFrame)
                    it.updateStackFrames
                }
            )
            if(updateStackFrames) {
                // The stack frames must be created again, since they might change with the new sourceReference
                return getSourceReferenceWrappedStackFrames(debugStackEntry, debugConnection)
            }
        }
        return wrappedStackFrames
    }

    interface DebugFrame {
        fun getDebugPauseHandler(): DebugPauseHandler
        fun unpause()
        fun shouldWrapInSourceReference(path: String): Either<NewSourceReferenceWrapper, ExistingSourceReferenceWrapper>?
        fun onExitFrame()
        fun onContinue(stackEntry: DebugFrameStack.Entry)
    }

    // Doesn't need additionalSourceChanges, because stack frames will be updated anyway, at which point an ExistingSourceReferenceWrapper should be returned
    class NewSourceReferenceWrapper(
        val sourceReferenceCallback: (Int) -> Unit,
        val content: (Int) -> String
    )

    class ExistingSourceReferenceWrapper(
        val sourceReferenceId: Int?,
        val additionalSourceChanges: (Source) -> Unit,
        val updateStackFrames: Boolean
    )

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