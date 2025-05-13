package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.command.CommandSource
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.resource.ResourcePackManager
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.world.SaveProperties
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.console.PreLaunchLogListener
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.helper.getDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.helper.setupOneTimeDebugTarget
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem
import net.papierkorb2292.command_crafter.helper.SizeLimitedCallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ReloadCommandAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.set

class DirectServerConnection(val server: MinecraftServer) : MinecraftServerConnection {
    companion object {
        const val SERVER_LOG_CHANNEL = "server"
        private const val COMMAND_EXECUTOR_NAME = "{DirectServerConnection}"
    }

    private val commandDispatcherFactory: (CommandManager) -> CommandDispatcher<CommandSource> = { commandManager: CommandManager ->
        val root = limitCommandTreeForSource(commandManager, ServerCommandSource(
            CommandOutput.DUMMY,
            Vec3d.ZERO,
            Vec2f.ZERO,
            null,
            functionPermissionLevel,
            "",
            ScreenTexts.EMPTY,
            null,
            null
        ))
        CommandCrafter.removeLiteralsStartingWithForwardsSlash(root)
        CommandDispatcher(root)
    }.memoizeLast()

    override val dynamicRegistryManager: DynamicRegistryManager
        get() = server.registryManager
    override val commandDispatcher: CommandDispatcher<CommandSource>
        get() = commandDispatcherFactory(server.commandManager)
    override val functionPermissionLevel = server.functionPermissionLevel
    override val serverLog: Log? =
        if(server.isDedicated)
            object : Log {
                override val name
                    get() = SERVER_LOG_CHANNEL
                override fun addMessageCallback(callback: SizeLimitedCallbackLinkedBlockingQueue.Callback<String>) {
                    PreLaunchLogListener.addLogListener(callback)
                }
            }
        else null
    override val commandExecutor = object : CommandExecutor {
        override fun executeCommand(command: String) {
            server.commandSource
            server.commandManager.executeWithPrefix(ServerCommandSource(
                CommandOutput.DUMMY,
                Vec3d.ZERO,
                Vec2f.ZERO,
                server.overworld,
                functionPermissionLevel,
                COMMAND_EXECUTOR_NAME,
                Text.literal(COMMAND_EXECUTOR_NAME),
                server,
                null
            ), command)
        }
    }
    override val debugService = object : ServerDebugConnectionService {
        private val currentPauseActions = mutableMapOf<EditorDebugConnection, DebugPauseActions>()
        private val wrappedEditorDebugConnections = mutableMapOf<EditorDebugConnection, EditorDebugConnection>()

        override fun setupEditorDebugConnection(editorDebugConnection: EditorDebugConnection) {
            val wrapped = object : EditorDebugConnection {
                override val lifecycle: EditorDebugConnection.Lifecycle
                    get() = editorDebugConnection.lifecycle
                override val oneTimeDebugTarget: EditorDebugConnection.DebugTarget?
                    get() = editorDebugConnection.oneTimeDebugTarget
                override val nextSourceReference: Int
                    get() = editorDebugConnection.nextSourceReference
                override val suspendServer: Boolean
                    get() = editorDebugConnection.suspendServer
                override fun pauseStarted(
                    actions: DebugPauseActions,
                    args: StoppedEventArguments,
                    variables: VariablesReferencer,
                ) {
                    currentPauseActions[editorDebugConnection] = actions
                    editorDebugConnection.pauseStarted(actions, args, variables)
                }
                override fun pauseEnded() {
                    currentPauseActions.remove(editorDebugConnection)
                    editorDebugConnection.pauseEnded()
                }
                override fun isPaused() =
                    editorDebugConnection.isPaused()
                override fun updateReloadedBreakpoint(update: BreakpointEventArguments) =
                    editorDebugConnection.updateReloadedBreakpoint(update)
                override fun reserveBreakpointIds(count: Int) =
                    editorDebugConnection.reserveBreakpointIds(count)
                override fun popStackFrames(stackFrames: Int) =
                    editorDebugConnection.popStackFrames(stackFrames)
                override fun pushStackFrames(stackFrames: List<MinecraftStackFrame>) =
                    editorDebugConnection.pushStackFrames(stackFrames)
                override fun output(args: OutputEventArguments) =
                    editorDebugConnection.output(args)
                override fun onSourceReferenceAdded() =
                    editorDebugConnection.onSourceReferenceAdded()
            }
            wrapped.setupOneTimeDebugTarget(server)
            wrappedEditorDebugConnections[editorDebugConnection] = wrapped
        }

        override fun setBreakpoints(
            breakpoints: Array<UnparsedServerBreakpoint>,
            source: Source,
            fileType: PackContentFileType,
            id: PackagedId,
            editorDebugConnection: EditorDebugConnection,
        ): CompletableFuture<SetBreakpointsResponse> {
            val wrapped = wrappedEditorDebugConnections[editorDebugConnection]
                ?: return CompletableFuture.failedFuture(IllegalArgumentException("EditorDebugConnection not initialized"))
            return CompletableFuture.completedFuture(SetBreakpointsResponse().also {
                it.breakpoints = server.getDebugManager().setBreakpoints(
                    breakpoints,
                    fileType,
                    id,
                    wrapped,
                    source.sourceReference
                )
            })
        }

        override fun retrieveSourceReference(
            sourceReference: Int,
            editorDebugConnection: EditorDebugConnection,
        ): CompletableFuture<SourceResponse?> {
            val wrapped = wrappedEditorDebugConnections[editorDebugConnection]
                ?: return CompletableFuture.failedFuture(IllegalArgumentException("EditorDebugConnection not initialized"))
            return CompletableFuture.completedFuture(server.getDebugManager().retrieveSourceReference(wrapped, sourceReference))
        }

        override fun removeEditorDebugConnection(editorDebugConnection: EditorDebugConnection) {
            val wrapped = wrappedEditorDebugConnections.remove(editorDebugConnection) ?: return
            currentPauseActions.remove(editorDebugConnection)?.continue_()
            server.getDebugManager().removeDebugConnection(wrapped)
        }
    }

    override val contextCompletionProvider = object : ContextCompletionProvider {
        override fun getCompletions(fullInput: DirectiveStringReader<AnalyzingResourceCreator>): CompletableFuture<List<CompletionItem>> {
            val resetMappingInfo = fullInput.fileMappingInfo.copy()
            resetMappingInfo.readCharacters = 0
            resetMappingInfo.skippedChars = 0
            val analyzingResult = AnalyzingResult(resetMappingInfo, Position())
            LanguageManager.analyse(DirectiveStringReader(resetMappingInfo, fullInput.dispatcher, fullInput.resourceCreator), server.commandSource, analyzingResult, LanguageManager.DEFAULT_CLOSURE)
            return analyzingResult.getCompletionProviderForCursor(fullInput.cursor)
                ?.dataProvider?.invoke(fullInput.cursor)
                ?: CompletableFuture.completedFuture(listOf())
        }
    }

    private var datapackReloadWaitFuture: CompletableFuture<*>? = CompletableFuture.completedFuture(Unit)

    override val datapackReloader: () -> Unit = {
        val datapackReloadWaitFuture = datapackReloadWaitFuture
        if(datapackReloadWaitFuture != null) {
            this.datapackReloadWaitFuture = null // No other reloads should be scheduled until this one starts
            datapackReloadWaitFuture.whenCompleteAsync({ _, _ ->
                val completableFuture = CompletableFuture<Unit>()
                this.datapackReloadWaitFuture = completableFuture
                val resourcePackManager: ResourcePackManager = server.dataPackManager
                val saveProperties: SaveProperties = server.saveProperties
                val previouslyEnabledDatapacks = resourcePackManager.enabledIds
                val allEnabledDatapacks = ReloadCommandAccessor.callFindNewDataPacks(resourcePackManager, saveProperties, previouslyEnabledDatapacks)

                server.reloadResources(allEnabledDatapacks).whenComplete { _, _ ->
                    completableFuture.complete(Unit)
                }
            }, server)
        }
    }

    override fun createScoreboardStorageFileSystem() =
        ServerScoreboardStorageFileSystem(server)
}