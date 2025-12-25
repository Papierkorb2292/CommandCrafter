package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.network.chat.CommonComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.commands.Commands
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.storage.WorldData
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

        val WORLDGEN_DEVTOOLS_RELOAD_REGISTRIES_ID = Identifier.parse("reload_registries")

        private var datapackReloadWaitFuture: CompletableFuture<Unit>? = CompletableFuture.completedFuture(Unit)
        private var datapackReloadReconfigureFuture: CompletableFuture<Unit>? = null

        fun registerReconfigureCompletedCheck() {
            ServerTickEvents.END_SERVER_TICK.register {
                checkCompletedReconfigureAfterReload(it)
            }
        }

        @Synchronized
        private fun reloadDatapacks(server: MinecraftServer) {
            val datapackReloadWaitFuture = datapackReloadWaitFuture
            if(datapackReloadWaitFuture != null) {
                this.datapackReloadWaitFuture = null // No other reloads should be scheduled until this one starts
                datapackReloadWaitFuture.whenCompleteAsync({ _, _ ->
                    val completableFuture = CompletableFuture<Unit>()
                    this.datapackReloadWaitFuture = completableFuture
                    val resourcePackManager: PackRepository = server.packRepository
                    val saveProperties: WorldData = server.worldData
                    val previouslyEnabledDatapacks = resourcePackManager.selectedIds
                    val allEnabledDatapacks = ReloadCommandAccessor.callDiscoverNewPacks(resourcePackManager, saveProperties, previouslyEnabledDatapacks)

                    server.createCommandSourceStack()
                        .sendSuccess({ Component.translatable("commands.reload.success") }, true)
                    server.reloadResources(allEnabledDatapacks).whenComplete { _, _ ->
                        datapackReloadReconfigureFuture = completableFuture
                        checkCompletedReconfigureAfterReload(server)
                    }
                }, server)
            }
        }

        @Synchronized
        private fun checkCompletedReconfigureAfterReload(server: MinecraftServer) {
            val future = datapackReloadReconfigureFuture ?: return
            // Wait until all players are done logging into the world, because otherwise mods like
            // Worldgen Devtools would push them back into configuration phase while still sending them some of
            // the initial play packets, leading to a disconnect due to unknown packets
            val allPlayersConfigured = server.connection.connections.all {
                val packetListener = it.packetListener
                packetListener is ServerGamePacketListenerImpl && packetListener.isAcceptingMessages // isConnectionOpen would be false if a reconfiguration has been requested and no new network handler has been created yet
            }
            if(allPlayersConfigured) {
                datapackReloadReconfigureFuture = null
                future.complete(Unit)
            }
        }
    }

    private val commandDispatcherFactory: (Commands) -> CommandDispatcher<SharedSuggestionProvider> = { commandManager: Commands ->
        val root = limitCommandTreeForSource(commandManager, Commands.createCompilationContext(functionPermissions))
        CommandCrafter.removeLiteralsStartingWithForwardsSlash(root)
        CommandDispatcher(root)
    }.memoizeLast()

    override val dynamicRegistryManager: RegistryAccess
        get() = server.registryAccess()
    override val commandDispatcher: CommandDispatcher<SharedSuggestionProvider>
        get() = commandDispatcherFactory(server.commands)
    override val functionPermissions = server.functionCompilationPermissions
    override val serverLog: Log? =
        if(server.isDedicatedServer)
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
            server.createCommandSourceStack()
            server.commands.performPrefixedCommand(
                CommandSourceStack(
                CommandSource.NULL,
                Vec3.ZERO,
                Vec2.ZERO,
                server.overworld(),
                functionPermissions,
                COMMAND_EXECUTOR_NAME,
                Component.literal(COMMAND_EXECUTOR_NAME),
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
            val resetReader = DirectiveStringReader(resetMappingInfo, fullInput.dispatcher, fullInput.resourceCreator)
            resetReader.resourceCreator.suggestionRequestInfo = AnalyzingResourceCreator.SuggestionRequestInfo(fullInput.cursor, true)
            LanguageManager.analyse(resetReader, server.createCommandSourceStack(), analyzingResult, LanguageManager.DEFAULT_CLOSURE)
            return analyzingResult.getCompletionProviderForCursor(fullInput.cursor)
                ?.dataProvider?.invoke(fullInput.cursor)
                ?: CompletableFuture.completedFuture(listOf())
        }
    }

    override val datapackReloader = { reloadDatapacks(server) }

    override val canReloadWorldgen: Boolean
        get() {
            val rule = BuiltInRegistries.GAME_RULE.getValue(WORLDGEN_DEVTOOLS_RELOAD_REGISTRIES_ID) ?: return false
            val value = server.worldData.gameRules.get(rule)
            if(value !is Boolean) {
                CommandCrafter.LOGGER.debug("Unexpected type of value for reload_registries game rule: {}", value)
                return false
            }
            return value
        }

    override fun createScoreboardStorageFileSystem() =
        ServerScoreboardStorageFileSystem(server)
}