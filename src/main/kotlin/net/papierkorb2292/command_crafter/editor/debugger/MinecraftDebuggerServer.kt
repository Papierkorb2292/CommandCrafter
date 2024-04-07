package net.papierkorb2292.command_crafter.editor.debugger

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.withLock
import net.papierkorb2292.command_crafter.editor.CommandCrafterDebugClient
import net.papierkorb2292.command_crafter.editor.EditorService
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min


class MinecraftDebuggerServer(private var minecraftServer: MinecraftServerConnection) : IDebugProtocolServer, EditorService {
    companion object {
        const val BREAKPOINT_AT_NO_CODE_REJECTION_REASON = "No debuggable code at this location"
        const val SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON = "Server does not support debugging"
        const val FILE_TYPE_NOT_DETERMINED_REJECTION_REASON = "File type not determined by path"
        const val FILE_TYPE_NOT_SUPPORTED_REJECTION_REASON = "File type not supported by server"
        const val DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON = "No debug information available for this function"
        const val UNKNOWN_FUNCTION_REJECTION_REASON = "Function not known to server"
        const val DYNAMIC_BREAKPOINT_REJECTION_REASON = "Dynamic breakpoint will be validated once function is called"

        fun rejectAllBreakpoints(breakpoints: Array<UnparsedServerBreakpoint>, reason: String, source: Source? = null)
            = Array(breakpoints.size) { rejectBreakpoint(breakpoints[it], reason, source) }
        fun <T> rejectAllBreakpoints(breakpoints: Iterable<ServerBreakpoint<T>>, reason: String, source: Source? = null)
            = breakpoints.map { rejectBreakpoint(it.unparsed, reason, source) }

        fun rejectBreakpoint(breakpoint: UnparsedServerBreakpoint, reason: String, source: Source? = null) = Breakpoint().apply {
            line = breakpoint.sourceBreakpoint.line
            column = breakpoint.sourceBreakpoint.column
            isVerified = false
            this.source = source
            id = breakpoint.id
            message = reason
        }

        fun acceptBreakpoint(breakpoint: UnparsedServerBreakpoint, source: Source? = null) = Breakpoint().apply {
            line = breakpoint.sourceBreakpoint.line
            column = breakpoint.sourceBreakpoint.column
            isVerified = true
            this.source = source
            id = breakpoint.id
        }
    }

    private var client: CommandCrafterDebugClient? = null

    private var debugPauseActions: DebugPauseActions? = null
    private var variablesReferencer: VariablesReferencer? = null
    /** Must be used with [stackTraceLock] */
    private val stackTrace = ArrayList<Pair<StackFrame, Array<Scope>>>()
    private val stackTraceLock = ReentrantLock()

    private var nextBreakpointId = 0
    private val nextBreakpointIdLock = SynchronizedObject()

    private val editorDebugConnection = object : EditorDebugConnection {
        override fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer) {
            debugPauseActions = actions
            variablesReferencer = variables
            if(args.threadId == null)
                args.threadId = 0
            client?.stopped(args)
        }

        override fun pauseEnded() {
            debugPauseActions = null
        }

        override fun isPaused() = debugPauseActions != null

        override fun updateReloadedBreakpoint(update: BreakpointEventArguments) {
            val source = update.breakpoint.source
            mapSourceToDatapack(source).thenAccept {
                client?.breakpoint(update)
            }
        }

        override fun reserveBreakpointIds(count: Int) =
            synchronized(nextBreakpointIdLock) {
                val start = nextBreakpointId
                nextBreakpointId += count
                CompletableFuture.completedFuture(start)
            }

        override fun popStackFrames(stackFrames: Int) {
            stackTraceLock.withLock {
                stackTrace.subList(max(stackTrace.size - stackFrames, 0), stackTrace.size).clear()
            }
        }

        override fun pushStackFrames(stackFrames: List<MinecraftStackFrame>) {
            // Make sure that lock() and unlock() are called on the same thread
            val lockExecutor = Executors.newSingleThreadExecutor()
            CompletableFuture.runAsync({
                stackTraceLock.lock()
            }, lockExecutor).thenCompose {
                CompletableFuture.allOf(*stackFrames.map { frame ->
                    val frameRange = frame.visualContext.range
                    stackTrace += StackFrame().apply {
                        id = stackTrace.size
                        name = frame.name
                        line = frameRange.start.line
                        column = frameRange.start.character
                        endLine = frameRange.end.line
                        endColumn = frameRange.end.character
                        source = frame.visualContext.source
                        presentationHint = frame.presentationHint
                    } to frame.variableScopes
                    mapSourceToDatapack(frame.visualContext.source)
                }.toTypedArray())
            }.whenCompleteAsync({ _, _ ->
                stackTraceLock.unlock()
            }, lockExecutor)
        }

        override fun onPauseLocationSkipped() {
            client?.output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.IMPORTANT
                output = "Skipped pause location"
            })
        }
    }

    private var initializeArgs = InitializeRequestArguments()

    /**
     * If a source path can't be parsed by [PackContentFileType.parsePath],
     * this function returns a CompletableFuture which resolves to **false**
     * after child sources have been mapped.
     *
     * Otherwise, it sends a request to the editor to search for fitting resources.
     * Upon receiving a response, the provided Source will be updated and the
     * returned CompletableFuture will be completed with **true** if a file for this source
     * and all child sources could be found, **false** otherwise.
     */
    fun mapSourceToDatapack(source: Source): CompletableFuture<Boolean> {
        val client = client ?: return CompletableFuture.completedFuture(false)

        val mappingChildSourcesFutures = source.sources?.map { mapSourceToDatapack(it) }?.toTypedArray()

        val parsedPath = PackContentFileType.parsePath(Path.of(source.path))
            ?: return if(mappingChildSourcesFutures != null) {
                CompletableFuture.allOf(*mappingChildSourcesFutures)
                    .thenApply { false }
            } else CompletableFuture.completedFuture(false)

        val mappingCurrentSourceFuture = PackContentFileType.findWorkspaceResourceFromIdAndPackContentFileType(
            parsedPath.id, parsedPath.type, client
        ).thenApply {
            source.path = it ?: return@thenApply false
            true
        }

        return if(mappingChildSourcesFutures != null) {
            CompletableFuture.allOf(*mappingChildSourcesFutures, mappingCurrentSourceFuture).thenApply {
                mappingCurrentSourceFuture.get() && mappingChildSourcesFutures.all { it.get() }
            }
        } else mappingCurrentSourceFuture
    }

    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        initializeArgs = args
        return CompletableFuture.completedFuture(Capabilities().apply {
            supportsConfigurationDoneRequest = true
            supportsSteppingGranularity = true
            supportsSetVariable = true
            supportsStepInTargetsRequest = true
        })
    }

    override fun configurationDone(args: ConfigurationDoneArguments?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun launch(args: MutableMap<String, Any>): CompletableFuture<Void> {
        stackTraceLock.withLock {
            stackTrace.clear()
        }
        debugPauseActions = null
        nextBreakpointId = 0
        return CompletableFuture.completedFuture(null)
    }

    override fun disconnect(args: DisconnectArguments): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun terminate(args: TerminateArguments): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    val breakpoints: MutableMap<PackContentFileType.ParsedPath, Pair<Source, Array<UnparsedServerBreakpoint>>> = mutableMapOf()

    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {
        val unparsedBreakpoints = synchronized(nextBreakpointIdLock) {
            Array(args.breakpoints.size) {
                UnparsedServerBreakpoint(nextBreakpointId++, args.source.sourceReference, args.breakpoints[it])
            }
        }

        fun rejectAll(reason: String): CompletableFuture<SetBreakpointsResponse> {
            val response = SetBreakpointsResponse()
            response.breakpoints = rejectAllBreakpoints(unparsedBreakpoints, reason, args.source)
            return CompletableFuture.completedFuture(response)
        }
        val parsedPath = PackContentFileType.parsePath(Path.of(args.source.path)) ?: return rejectAll(FILE_TYPE_NOT_DETERMINED_REJECTION_REASON)
        if(unparsedBreakpoints.isEmpty()) {
            breakpoints.remove(parsedPath)
        } else {
            breakpoints[parsedPath] = args.source to unparsedBreakpoints
        }
        val debugService = minecraftServer.debugService ?: return rejectAll(SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON)
        return debugService.setBreakpoints(unparsedBreakpoints, args.source, parsedPath.type, parsedPath.id, editorDebugConnection)
            .thenCompose { response ->
                CompletableFuture.allOf(*response.breakpoints.toList().mapNotNull {
                    mapSourceToDatapack(it.source ?: return@mapNotNull null)
                }.toTypedArray()).thenApply { response }
            }
    }

    override fun threads(): CompletableFuture<ThreadsResponse> {
        val response = ThreadsResponse()
        val thread = Thread()
        thread.id = 0
        thread.name = "Main Thread"
        response.threads = arrayOf(thread)
        return CompletableFuture.completedFuture(response)
    }

    override fun next(args: NextArguments): CompletableFuture<Void> {
        debugPauseActions?.next(args.granularity ?: SteppingGranularity.STATEMENT)
        debugPauseActions = null
        return CompletableFuture.completedFuture(null)
    }
    override fun stepIn(args: StepInArguments): CompletableFuture<Void> {
        debugPauseActions?.stepIn(args.granularity ?: SteppingGranularity.STATEMENT, args.targetId)
        debugPauseActions = null
        return CompletableFuture.completedFuture(null)
    }
    override fun stepOut(args: StepOutArguments): CompletableFuture<Void> {
        debugPauseActions?.stepOut(args.granularity ?: SteppingGranularity.STATEMENT)
        debugPauseActions = null
        return CompletableFuture.completedFuture(null)
    }

    override fun stepInTargets(args: StepInTargetsArguments): CompletableFuture<StepInTargetsResponse> {
        return debugPauseActions?.stepInTargets(args.frameId) ?: CompletableFuture.completedFuture(StepInTargetsResponse())
    }

    override fun continue_(args: ContinueArguments): CompletableFuture<ContinueResponse> {
        debugPauseActions?.continue_()
        debugPauseActions = null
        return CompletableFuture.completedFuture(ContinueResponse().apply { allThreadsContinued = true })
    }

    override fun stackTrace(args: StackTraceArguments): CompletableFuture<StackTraceResponse> {
        return CompletableFuture.supplyAsync {
            stackTraceLock.withLock {
                val startFrameNumber = stackTrace.size - (args.startFrame ?: 0)
                val amount = args.levels.run {
                    if(this == null || this == 0) stackTrace.size
                    else min(this, startFrameNumber)
                }
                val startFrameIndex = startFrameNumber - 1
                StackTraceResponse().apply {
                    stackFrames = Array(amount) {
                        stackTrace[startFrameIndex - it].first
                    }
                    totalFrames = stackTrace.size
                }
            }
        }
    }

    override fun scopes(args: ScopesArguments): CompletableFuture<ScopesResponse> {
        return CompletableFuture.supplyAsync {
            stackTraceLock.withLock {
                val stackTrace = stackTrace
                val frameId = args.frameId
                ScopesResponse().apply {
                    scopes =
                        if(stackTrace.size > frameId) stackTrace[frameId].second
                        else arrayOf()
                }
            }
        }
    }

    override fun variables(args: VariablesArguments): CompletableFuture<VariablesResponse> {
        val variablesReferencer = variablesReferencer ?:
            return CompletableFuture.completedFuture(VariablesResponse().apply { variables = arrayOf() })
        return variablesReferencer.getVariables(args).thenApply { variables ->
            VariablesResponse().apply {
                this.variables = variables
            }
        }
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<SetVariableResponse?> {
        val variablesReferencer = variablesReferencer ?: return CompletableFuture.completedFuture(null)
        return variablesReferencer.setVariable(args).thenApply {
            if(it != null) {
                if(it.invalidateVariables && initializeArgs.supportsInvalidatedEvent) {
                    client?.invalidated(InvalidatedEventArguments().apply {
                        areas = arrayOf(InvalidatedAreas.VARIABLES)
                        threadId = 0
                    })
                }
                it.response
            } else null
        }
    }

    override fun source(args: SourceArguments): CompletableFuture<SourceResponse> {
        val reference = args.source.sourceReference ?: throw IllegalArgumentException("Source reference must be provided")
        val debugService = minecraftServer.debugService ?: throw UnsupportedOperationException(SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON)
        return debugService.retrieveSourceReference(reference, editorDebugConnection)
    }

    override fun setMinecraftServerConnection(connection: MinecraftServerConnection) {
        minecraftServer = connection
        stackTraceLock.withLock {
            stackTrace.clear()
        }
        if(debugPauseActions != null) {
            client?.continued(ContinuedEventArguments().apply { threadId =  0 })
            debugPauseActions = null
        }
        val client = client ?: return
        val debugService = connection.debugService
        if(debugService == null) {
            for((source, breakpoints) in breakpoints.values) {
                for(breakpoint in breakpoints) {
                    val args = BreakpointEventArguments()
                    args.breakpoint = rejectBreakpoint(breakpoint, SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON, source)
                    args.reason = BreakpointEventArgumentsReason.CHANGED
                    client.breakpoint(args)
                }
            }
            return
        }

        fun sendBreakpointToClient(breakpoint: Breakpoint) {
            val args = BreakpointEventArguments()
            args.breakpoint = breakpoint
            args.reason = BreakpointEventArgumentsReason.CHANGED
            client.breakpoint(args)
        }

        for((path, data) in breakpoints) {
            debugService.setBreakpoints(data.second, data.first, path.type, path.id, editorDebugConnection).thenAccept {
                for(breakpoint in it.breakpoints) {
                    val source = breakpoint.source
                    if(source == null) {
                        sendBreakpointToClient(breakpoint)
                        continue
                    }
                    mapSourceToDatapack(source).thenAccept {
                        sendBreakpointToClient(breakpoint)
                    }
                }
            }
        }
    }

    override fun onClosed() {
        minecraftServer.debugService?.removeEditorDebugConnection(editorDebugConnection)
    }

    fun connect(client: CommandCrafterDebugClient) {
        this.client = client
    }
}