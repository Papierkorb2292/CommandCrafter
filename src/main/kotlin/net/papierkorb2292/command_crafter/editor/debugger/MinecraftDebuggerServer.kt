package net.papierkorb2292.command_crafter.editor.debugger

import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.EditorService
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.IdFactory
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.asSequence

class MinecraftDebuggerServer(private var minecraftServer: MinecraftServerConnection) : IDebugProtocolServer, EditorService {
    companion object {
        const val BREAKPOINT_AT_NO_CODE_REJECTION_REASON = "No debuggable code at this location"
        const val SERVER_NOT_SUPPORTING_DEBUGGING_REJECTION_REASON = "Server does not support debugging"
        const val FILE_TYPE_NOT_DETERMINED_REJECTION_REASON = "File type not determined by path"
        const val FILE_TYPE_NOT_SUPPORTED_REJECTION_REASON = "File type not supported by server"
        const val DEBUG_INFORMATION_NOT_SAVED_REJECTION_REASON = "No debug information available for this function"
        const val UNKNOWN_FUNCTION_REJECTION_REASON = "Function not known to server"

        fun rejectAllBreakpoints(breakpoints: Array<UnparsedServerBreakpoint>, reason: String, source: Source? = null)
            = Array(breakpoints.size) { rejectBreakpoint(breakpoints[it], reason, source) }
        fun <T> rejectAllBreakpoints(breakpoints: List<ServerBreakpoint<T>>, reason: String, source: Source? = null)
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

    private var client: IDebugProtocolClient? = null

    private var debugPauseActions: DebugPauseActions? = null
    private var variablesReferencer: VariablesReferencer? = null
    private var dataFolders: Collection<Path> = emptyList()
    private val stackTrace = ArrayList<Pair<StackFrame, Array<Scope>>>()

    private val ids = IdFactory()
    private val editorDebugConnection = object : EditorDebugConnection {
        override fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer) {
            debugPauseActions = actions
            variablesReferencer = variables
            client?.stopped(args)
        }

        override fun pauseEnded() {
            debugPauseActions = null
        }

        override fun isPaused() = debugPauseActions != null

        override fun updateReloadedBreakpoint(breakpoint: Breakpoint) {
            val client = client ?: return
            val args = BreakpointEventArguments()
            args.breakpoint = breakpoint
            args.reason = BreakpointEventArgumentsReason.CHANGED
            client.breakpoint(args)
        }

        override fun popStackFrames(stackFrames: Int) {
            stackTrace.subList(max(stackTrace.size - stackFrames, 0), stackTrace.size).clear()
        }

        override fun pushStackFrames(stackFrames: List<MinecraftStackFrame>) {
            stackFrames.forEachIndexed { index, frame ->
                val frameRange = frame.visualContext.range
                stackTrace += StackFrame().apply {
                    id = index
                    name = frame.name
                    line = frameRange.start.line
                    column = frameRange.start.character
                    endLine = frameRange.end.line
                    endColumn = frameRange.end.character
                    val visualContext = frame.visualContext
                    source =  dataFolders.firstNotNullOfOrNull { dataFolder ->
                        val path = dataFolder.resolve(visualContext.fileType.toPath(frame.visualContext.fileId))
                        if (!path.exists()) null
                        else Source().apply {
                            this.path = path.toString()
                            name = visualContext.fileId.toString()
                        }
                    }
                } to frame.variableScopes
            }
        }
    }

    private var initializeArgs = InitializeRequestArguments()

    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        initializeArgs = args
        return CompletableFuture.completedFuture(Capabilities().apply {
            supportsConfigurationDoneRequest = true
            supportsSteppingGranularity = true
            supportsSetVariable = true
        })
    }

    override fun configurationDone(args: ConfigurationDoneArguments?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun launch(args: MutableMap<String, Any>): CompletableFuture<Void> {
        stackTrace.clear()
        debugPauseActions = null
        ids.reset()
        val dataFoldersArg = args["dataFolders"]
        if(dataFoldersArg !is Collection<*>) {
            return CompletableFuture.failedFuture(IllegalArgumentException("'dataFolders' argument must be an array"))
        }
        dataFolders = dataFoldersArg.flatMap {
            if (it !is String) {
                CommandCrafter.LOGGER.warn("Encountered invalid data folder path while starting debugger: $it")
                return@flatMap emptySequence()
            }
            val path = Path.of(it)
            if (!path.exists())
                return@flatMap emptySequence()
            Files.walk(path).filter { candidate ->
                candidate.isDirectory() && candidate.fileName.toString() == "data"
            }.asSequence()
        }
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
        val unparsedBreakpoints = Array(args.breakpoints.size) {
            UnparsedServerBreakpoint(ids.nextId(), args.breakpoints[it])
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
        debugPauseActions?.stepIn(args.granularity ?: SteppingGranularity.STATEMENT)
        debugPauseActions = null
        return CompletableFuture.completedFuture(null)
    }
    override fun stepOut(args: StepOutArguments): CompletableFuture<Void> {
        debugPauseActions?.stepOut(args.granularity ?: SteppingGranularity.STATEMENT)
        debugPauseActions = null
        return CompletableFuture.completedFuture(null)
    }

    override fun continue_(args: ContinueArguments): CompletableFuture<ContinueResponse> {
        debugPauseActions?.continue_()
        debugPauseActions = null
        return CompletableFuture.completedFuture(ContinueResponse().apply { allThreadsContinued = true })
    }

    override fun stackTrace(args: StackTraceArguments): CompletableFuture<StackTraceResponse> {
        val startFrameNumber = stackTrace.size - (args.startFrame ?: 0)
        val amount = args.levels.run {
            if(this == null || this == 0) stackTrace.size
            else min(this, startFrameNumber)
        }
        val startFrameIndex = startFrameNumber - 1
        return CompletableFuture.completedFuture(StackTraceResponse().apply {
            stackFrames = Array(amount) {
                stackTrace[startFrameIndex - it].first
            }
            totalFrames = stackTrace.size
        })
    }

    override fun scopes(args: ScopesArguments): CompletableFuture<ScopesResponse> {
        val stackTrace = stackTrace
        val frameId = args.frameId
        return CompletableFuture.completedFuture(ScopesResponse().apply {
            scopes =
                if(stackTrace.size > frameId) stackTrace[frameId].second
                else arrayOf()
        })
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

    override fun setMinecraftServerConnection(connection: MinecraftServerConnection) {
        minecraftServer = connection
        stackTrace.clear()
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
        for((path, data) in breakpoints) {
            debugService.setBreakpoints(data.second, data.first, path.type, path.id, editorDebugConnection).thenAccept {
                for(breakpoint in it.breakpoints) {
                    val args = BreakpointEventArguments()
                    args.breakpoint = breakpoint
                    args.reason = BreakpointEventArgumentsReason.CHANGED
                    client.breakpoint(args)
                }
            }
        }
    }

    override fun onClosed() {
        minecraftServer.debugService?.removeEditorDebugConnection(editorDebugConnection)
    }

    fun connect(client: IDebugProtocolClient) {
        this.client = client
    }
}