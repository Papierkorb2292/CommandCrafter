package net.papierkorb2292.command_crafter.editor.debugger

import kotlinx.atomicfu.locks.SynchronizedObject
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.*
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.helper.withAcquired
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import kotlin.math.max
import kotlin.math.min

class MinecraftDebuggerServer(private var minecraftServer: MinecraftServerConnection) : IDebugProtocolServer, EditorService {
    companion object {
        const val BREAKPOINT_AT_NO_CODE_REJECTION_REASON = "No debuggable code at this location"
        const val SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON = "Server does not support debugging"
        const val FILE_TYPE_NOT_DETERMINED_REJECTION_REASON = "File type not determined by path"
        const val FILE_TYPE_NOT_SUPPORTED_REJECTION_REASON = "File type not supported by server"
        const val DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON = "No debug information available for this file"
        const val UNKNOWN_FUNCTION_REJECTION_REASON = "Function not known to server"
        const val DYNAMIC_BREAKPOINT_MESSAGE = "Dynamic breakpoint will be validated once function is called"

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
    /**
     * Uses the java Semaphore to allow for the lock to be released from another thread
     * than the one that acquired it (required for pushing debug frames)
     * */
    private val stackTraceLock = Semaphore(1, true)

    private var nextBreakpointId = 0
    private val nextBreakpointIdLock = SynchronizedObject()



    private val editorDebugConnection = object : EditorDebugConnection {
        override val lifecycle = EditorDebugConnection.Lifecycle()
        override val oneTimeDebugTarget: EditorDebugConnection.DebugTarget?
            get() = this@MinecraftDebuggerServer.oneTimeDebugTarget
        override var nextSourceReference = 1
        override val suspendServer: Boolean
            get() = this@MinecraftDebuggerServer.suspendServer

        init {
            lifecycle.shouldExitEvent.thenAccept {
                val client = client ?: return@thenAccept
                client.terminated(TerminatedEventArguments())
                client.exited(it)
            }
        }

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
            if(source == null) {
                client?.breakpoint(update)
                return
            }
            mapSourceToDatapack(source).thenAccept {
                val parsedPath = PackContentFileType.parsePath(source.path)
                if(parsedPath != null) {
                    val breakpointResource = ClientBreakpointResource(parsedPath.type, parsedPath.id, source.sourceReference)
                    val resourceBreakpoints = breakpoints.getOrPut(breakpointResource) { mutableMapOf<SourceBreakpoint, UnparsedServerBreakpoint>() to source }.first
                    val prevSourceBreakpoint = resourceBreakpoints.firstNotNullOfOrNull { (sourceBreakpoint, breakpoint) ->
                        if(breakpoint.id == update.breakpoint.id) sourceBreakpoint else null
                    }
                    val sourceBreakpoint = SourceBreakpoint().apply {
                        line = update.breakpoint.line
                        column = update.breakpoint.column
                        if(prevSourceBreakpoint != null) {
                            hitCondition = prevSourceBreakpoint.hitCondition
                            condition = prevSourceBreakpoint.condition
                            logMessage = prevSourceBreakpoint.logMessage
                        }
                    }
                    resourceBreakpoints[sourceBreakpoint] = UnparsedServerBreakpoint(
                        update.breakpoint.id, source.sourceReference, sourceBreakpoint
                    )
                }
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
            stackTraceLock.withAcquired {
                stackTrace.subList(max(stackTrace.size - stackFrames, 0), stackTrace.size).clear()
            }
        }

        override fun pushStackFrames(stackFrames: List<MinecraftStackFrame>) {
            stackTraceLock.acquire()
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
            }.toTypedArray()).whenCompleteAsync { _, _ ->
                stackTraceLock.release()
            }
        }

        override fun output(args: OutputEventArguments) {
            client?.output(args)
        }

        override fun onSourceReferenceAdded() {
            nextSourceReference++
        }
    }

    private var initializeArgs = InitializeRequestArguments()
    private var oneTimeDebugTarget: EditorDebugConnection.DebugTarget? = null
    private var suspendServer = true

    /**
     * Uses the source's path as pattern when searching for files. The first file that is found to
     * fit the pattern will replace the source's path. If no file is found, the path stays the same.
     *
     * The same procedure is recursively applied to child sources.
     */
    fun mapSourceToDatapack(source: Source): CompletableFuture<Void> {
        val client = client ?: return CompletableFuture.completedFuture(null)

        val mappingChildSourcesFuture = CompletableFuture.allOf(
            *source.sources
                ?.map { mapSourceToDatapack(it) }
                ?.toTypedArray()
                ?: emptyArray()
        )

        val path = source.path ?: return mappingChildSourcesFuture

        val mappingCurrentSourceFuture = client.getWorkspaceRoot().thenApply { workspaceUri ->
            if(workspaceUri == null) return@thenApply path
            val workspacePath = EditorURI.parseURI(workspaceUri).path
            val workspacePrefix = "**/" + workspacePath.substring(workspacePath.indexOfLast { it == '/' } + 1)
            if(!path.startsWith(workspacePrefix)) return@thenApply path
            return@thenApply "**" + path.substring(workspacePrefix.length)
        }.thenCompose { client.findFiles(it) }.thenApply {
            source.path = it?.firstOrNull() ?: return@thenApply
        }

        return CompletableFuture.allOf(mappingCurrentSourceFuture, mappingChildSourcesFuture)
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
        editorDebugConnection.lifecycle.configurationDoneEvent.complete(null)
        return CompletableFuture.completedFuture(null)
    }

    override fun launch(args: MutableMap<String, Any>): CompletableFuture<Void> {
        stackTraceLock.withAcquired {
            stackTrace.clear()
        }
        debugPauseActions = null
        nextBreakpointId = 0
        val functionToDebug = args["function"]
        val stopOnEntry = args["stopOnEntry"]
        if(functionToDebug != null) {
            val functionId = (functionToDebug as? String)?.let { Identifier.tryParse(it) }
            if(functionId == null) {
                client?.exited(ExitedEventArguments().apply { exitCode = 4 })
                throw IllegalArgumentException("'function' must be a valid function id")
            }
            if(stopOnEntry !is Boolean?) {
                client?.exited(ExitedEventArguments().apply { exitCode = 3 })
                throw IllegalArgumentException("'stopOnEntry' must be a boolean")
            }
            if(minecraftServer.debugService == null) {
                throw UnsupportedOperationException(SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON)
            }
            oneTimeDebugTarget = EditorDebugConnection.DebugTarget(PackContentFileType.FUNCTIONS_FILE_TYPE, functionId, stopOnEntry ?: false)
        } else if(stopOnEntry != null) {
            client?.exited(ExitedEventArguments().apply { exitCode = 2 })
            throw IllegalArgumentException("'stopOnEntry' must be used with 'function'")
        }
        val suspendServerArg = args["suspendServer"]
        if(suspendServerArg !is Boolean?)
            throw IllegalArgumentException("'suspendServer' must be a boolean")
        suspendServer = suspendServerArg ?: true
        return CompletableFuture.completedFuture(null)
    }

    override fun disconnect(args: DisconnectArguments): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun terminate(args: TerminateArguments): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    private val breakpoints: MutableMap<ClientBreakpointResource, Pair<MutableMap<SourceBreakpoint, UnparsedServerBreakpoint>, Source>> = mutableMapOf()

    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {

        val parsedPath = PackContentFileType.parsePath(args.source.path)
        val breakpointResource = parsedPath?.let { ClientBreakpointResource(parsedPath.type, parsedPath.id, args.source.sourceReference) }
        val prevBreakpoints = breakpoints[breakpointResource]?.first

        var unparsedBreakpoints: Array<UnparsedServerBreakpoint>? = null

        fun rejectAll(reason: String): CompletableFuture<SetBreakpointsResponse> {
            val response = SetBreakpointsResponse()
            response.breakpoints = rejectAllBreakpoints(
                unparsedBreakpoints ?: synchronized(nextBreakpointIdLock) {
                    Array(args.breakpoints.size) {
                        UnparsedServerBreakpoint(nextBreakpointId++, args.source.sourceReference, args.breakpoints[it])
                    }
                },
                reason,
                args.source
            )
            return CompletableFuture.completedFuture(response)
        }

        if(breakpointResource == null)
            return rejectAll(FILE_TYPE_NOT_DETERMINED_REJECTION_REASON)

        unparsedBreakpoints = synchronized(nextBreakpointIdLock) {
            Array(args.breakpoints.size) {
                prevBreakpoints?.get(args.breakpoints[it])
                    ?: UnparsedServerBreakpoint(nextBreakpointId++, args.source.sourceReference, args.breakpoints[it])
            }
        }

        if(unparsedBreakpoints.isEmpty()) {
            breakpoints.remove(breakpointResource)
        } else {

            breakpoints[breakpointResource] = unparsedBreakpoints.associateByTo(mutableMapOf()) { it.sourceBreakpoint } to args.source
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
    override fun pause(args: PauseArguments?): CompletableFuture<Void> {
        // Pause has no meaning for debugging Datapacks
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
            stackTraceLock.withAcquired {
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
            stackTraceLock.withAcquired {
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
        return debugService.retrieveSourceReference(reference, editorDebugConnection).thenApply {
            it ?: throw IllegalArgumentException("Source reference not found")
        }
    }

    override fun setMinecraftServerConnection(connection: MinecraftServerConnection) {
        if(oneTimeDebugTarget != null) {
            client?.exited(ExitedEventArguments().apply { exitCode = 1 })
            return
        }
        minecraftServer = connection
        stackTraceLock.withAcquired {
            stackTrace.clear()
        }
        if(debugPauseActions != null) {
            client?.continued(ContinuedEventArguments().apply { threadId =  0 })
            debugPauseActions = null
        }
        val client = client ?: return
        val debugService = connection.debugService
        if(debugService == null) {
            for(file in breakpoints.values) {
                for(breakpoint in file.first.values) {
                    val args = BreakpointEventArguments()
                    args.breakpoint = rejectBreakpoint(breakpoint, SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON, file.second)
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

        for((resource, data) in breakpoints) {
            debugService.setBreakpoints(data.first.values.toTypedArray(), data.second, resource.packContentFileType, resource.id, editorDebugConnection).thenAccept {
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

    override fun leave() {
        client?.terminated(TerminatedEventArguments())
    }

    fun connect(client: CommandCrafterDebugClient) {
        this.client = client
    }

    private data class ClientBreakpointResource(val packContentFileType: PackContentFileType, val id: PackagedId, val sourceReference: Int?)
}