package net.papierkorb2292.command_crafter.client

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Maps
import com.mojang.brigadier.CommandDispatcher
import io.netty.channel.local.LocalChannel
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientRegistries
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.resource.ResourceFactory
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.client.helper.SyncedRegistriesListConsumer
import net.papierkorb2292.command_crafter.editor.DirectServerConnection
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkDebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkVariablesReferencer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.helper.SizeLimitedCallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.mixin.editor.ClientConnectionAccessor
import net.papierkorb2292.command_crafter.networking.packets.*
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileNotificationC2SPacket
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileNotificationS2CPacket
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileRequestC2SPacket
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileResponseS2CPacket
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkServerConnection private constructor(private val client: MinecraftClient, private val initializePacket: InitializeNetworkServerConnectionS2CPacket) : MinecraftServerConnection {
    companion object {
        val currentGetVariablesRequests: MutableMap<UUID, CompletableFuture<Array<Variable>>> = mutableMapOf()
        val currentSetVariableRequests: MutableMap<UUID, CompletableFuture<VariablesReferencer.SetVariableResult?>> = mutableMapOf()
        val currentStepInTargetsRequests: MutableMap<UUID, CompletableFuture<StepInTargetsResponse>> = mutableMapOf()
        val currentSourceReferenceRequests: MutableMap<UUID, CompletableFuture<SourceResponse?>> = mutableMapOf()
        val currentContextCompletionRequests: MutableMap<UUID, CompletableFuture<List<CompletionItem>>> = mutableMapOf()

        val currentScoreboardStorageStatRequests = mutableMapOf<UUID, CompletableFuture<FileSystemResult<FileStat>>>()
        val currentScoreboardStorageReadDirectoryRequests = mutableMapOf<UUID, CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>>>()
        val currentScoreboardStorageCreateDirectoryRequests = mutableMapOf<UUID, CompletableFuture<FileSystemResult<Unit>>>()
        val currentScoreboardStorageReadFileRequests = mutableMapOf<UUID, CompletableFuture<FileSystemResult<ReadFileResult>>>()
        val currentScoreboardStorageWriteFileRequests = mutableMapOf<UUID, CompletableFuture<FileSystemResult<Unit>>>()
        val currentScoreboardStorageDeleteRequests = mutableMapOf<UUID, CompletableFuture<FileSystemResult<Unit>>>()
        val currentScoreboardStorageRenameRequests = mutableMapOf<UUID, CompletableFuture<FileSystemResult<Unit>>>()
        val currentScoreboardStorageLoadableStorageNamespacesRequests = mutableMapOf<UUID, CompletableFuture<LoadableStorageNamespaces>>()

        private var currentConnectionRequest: Pair<UUID, CompletableFuture<NetworkServerConnection>>? = null
        private val currentBreakpointRequests: MutableMap<UUID, (Array<Breakpoint>) -> Unit> = Maps.newHashMap()

        private val editorDebugConnections: BiMap<EditorDebugConnection, UUID> = HashBiMap.create()
        private val scoreboardStorageFileSystems: MutableMap<UUID, NetworkScoreboardStorageFileSystem> = mutableMapOf()

        private var receivedClientRegistries = ClientRegistries()
        private val receivedRegistryKeys = mutableSetOf<RegistryKey<out Registry<*>>>()
        private var receivedRegistryManager: DynamicRegistryManager? = null

        private val currentConnections = mutableListOf<NetworkServerConnection>()

        fun requestAndCreate(): CompletableFuture<NetworkServerConnection> {
            if(!ClientPlayNetworking.canSend(RequestNetworkServerConnectionC2SPacket.ID)) {
                return CompletableFuture.failedFuture(ServerConnectionNotSupportedException("Server doesn't support editor connections"))
            }
            val requestId = UUID.randomUUID()
            val result = CompletableFuture<NetworkServerConnection>()
            currentConnectionRequest = requestId to result
            ClientPlayNetworking.send(RequestNetworkServerConnectionC2SPacket(requestId, CommandCrafter.VERSION))
            return result
        }

        fun registerPacketHandlers() {
            ClientPlayNetworking.registerGlobalReceiver(InitializeNetworkServerConnectionS2CPacket.ID) handler@{ payload, context ->
                val currentRequest = currentConnectionRequest ?: return@handler
                if(payload.requestId != currentRequest.first) return@handler
                if(!payload.successful) {
                    currentRequest.second.completeExceptionally(
                        ServerConnectionNotSupportedException(
                            "Server didn't permit editor connection: ${payload.failReason}"
                        )
                    )
                    return@handler
                }
                val connection = NetworkServerConnection(context.client(), payload)
                currentConnections += connection
                currentRequest.second.complete(connection)
            }
            ClientPlayNetworking.registerGlobalReceiver(CommandCrafterDynamicRegistryS2CPacket.ID) { payload, _ ->
                if(payload.dynamicRegistry.registry in NetworkServerConnectionHandler.SYNCED_REGISTRY_KEYS)
                    receivedClientRegistries.putDynamicRegistry(payload.dynamicRegistry.registry, payload.dynamicRegistry.entries)
                if(payload.tags != null)
                    receivedClientRegistries.putTags(mapOf(payload.dynamicRegistry.registry to payload.tags))
                receivedRegistryKeys += payload.dynamicRegistry.registry
                if(NetworkServerConnectionHandler.SYNCED_REGISTRY_KEYS.all { it in receivedRegistryKeys }) {
                    (receivedClientRegistries as SyncedRegistriesListConsumer).`command_crafter$setSyncedRegistriesList`(NetworkServerConnectionHandler.SYNCED_REGISTRIES)
                    (receivedClientRegistries as ShouldCopyRegistriesContainer).`command_crafter$setShouldCopyRegistries`(true)
                    //All registries have been received
                    ClientCommandCrafter.getLoadedClientsideRegistries().combinedRegistries.combinedRegistryManager
                    receivedRegistryManager = receivedClientRegistries.createRegistryManager(
                        //No resource loading is required, because no common packs were specified
                        ResourceFactory.MISSING,
                        // Only matters if there are no registries to load, but there will always be some
                        null,
                        // False to load all tags, including of registries where the entries are not synced
                        false
                    )
                    receivedRegistryKeys.clear()
                    receivedClientRegistries = ClientRegistries()
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(LogMessageS2CPacket.ID) handler@{ payload, _ ->
                for(connection in currentConnections) {
                    connection.serverLog?.run { log.add(payload.logMessage) }
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(SetBreakpointsResponseS2CPacket.ID) { payload, _ ->
                currentBreakpointRequests.remove(payload.requestId)?.invoke(payload.breakpoints)
            }
            ClientPlayNetworking.registerGlobalReceiver(PopStackFramesS2CPacket.ID) { payload, _ ->
                val editorConnection = editorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.popStackFrames(payload.amount)
            }
            ClientPlayNetworking.registerGlobalReceiver(PushStackFramesS2CPacket.ID) { payload, _ ->
                val editorConnection = editorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.pushStackFrames(payload.stackFrames)
            }
            ClientPlayNetworking.registerGlobalReceiver(PausedUpdateS2CPacket.ID) handler@{ payload, context ->
                val editorConnection = editorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@handler
                val pause = payload.pause
                if(pause == null) editorConnection.pauseEnded()
                else editorConnection.pauseStarted(
                    NetworkDebugPauseActions(context.responseSender(), pause.first),
                    pause.second,
                    NetworkVariablesReferencer(context.responseSender(), pause.first)
                )
            }
            ClientPlayNetworking.registerGlobalReceiver(UpdateReloadedBreakpointS2CPacket.ID) { payload, _ ->
                editorDebugConnections.inverse()[payload.editorDebugConnection]?.updateReloadedBreakpoint(payload.update)
            }
            ClientPlayNetworking.registerGlobalReceiver(GetVariablesResponseS2CPacket.ID) { payload, _ ->
                currentGetVariablesRequests.remove(payload.requestId)?.complete(payload.variables)
            }
            ClientPlayNetworking.registerGlobalReceiver(SetVariableResponseS2CPacket.ID) { payload, _ ->
                currentSetVariableRequests.remove(payload.requestId)?.complete(payload.response)
            }
            ClientPlayNetworking.registerGlobalReceiver(StepInTargetsResponseS2CPacket.ID) { payload, _ ->
                currentStepInTargetsRequests.remove(payload.requestId)?.complete(payload.response)
            }
            ClientPlayNetworking.registerGlobalReceiver(DebuggerOutputS2CPacket.ID) { payload, _ ->
                editorDebugConnections.inverse()[payload.editorDebugConnection]?.output(payload.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(SourceReferenceResponseS2CPacket.ID) { payload, _ ->
                currentSourceReferenceRequests.remove(payload.requestId)?.complete(payload.source)
            }
            ClientPlayNetworking.registerGlobalReceiver(ReserveBreakpointIdsRequestS2CPacket.ID) { payload, context ->
                editorDebugConnections.inverse()[payload.editorDebugConnection]?.reserveBreakpointIds(payload.count)?.thenAccept {
                    context.responseSender().sendPacket(
                        ReserveBreakpointIdsResponseC2SPacket(it, payload.requestId)
                    )
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(DebuggerExitS2CPacket.ID) { payload, _ ->
                val editorConnection = editorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.lifecycle.shouldExitEvent.complete(payload.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(SourceReferenceAddedS2CPacket.ID) { payload, _ ->
                editorDebugConnections.inverse()[payload.editorDebugConnection]?.onSourceReferenceAdded()
            }
            ClientPlayNetworking.registerGlobalReceiver(ContextCompletionResponseS2CPacket.ID) { payload, _ ->
                currentContextCompletionRequests.remove(payload.requestId)?.complete(payload.completions)
            }
            ClientPlayNetworking.registerGlobalReceiver(ScoreboardStorageFileNotificationS2CPacket.DID_CHANGE_FILE_PACKET.id) { payload, _ ->
                scoreboardStorageFileSystems[payload.fileSystemId]?.currentOnDidChangeFileCallback?.invoke(payload.params)
            }
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.STAT_RESPONSE_PACKET,
                currentScoreboardStorageStatRequests
            )
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.READ_DIRECTORY_RESPONSE_PACKET,
                currentScoreboardStorageReadDirectoryRequests
            )
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.CREATE_DIRECTORY_RESPONSE_PACKET,
                currentScoreboardStorageCreateDirectoryRequests
            )
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.READ_FILE_RESPONSE_PACKET,
                currentScoreboardStorageReadFileRequests
            )
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.WRITE_FILE_RESPONSE_PACKET,
                currentScoreboardStorageWriteFileRequests
            )
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.DELETE_RESPONSE_PACKET,
                currentScoreboardStorageDeleteRequests
            )
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.RENAME_RESPONSE_PACKET,
                currentScoreboardStorageRenameRequests
            )
            registerScoreboardStorageResponseHandler(
                ScoreboardStorageFileResponseS2CPacket.LOADABLE_STORAGE_NAMESPACES_RESPONSE_PACKET,
                currentScoreboardStorageLoadableStorageNamespacesRequests
            )
            ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
                editorDebugConnections.clear()
                scoreboardStorageFileSystems.clear()
                currentConnections.clear()
            }
        }

        private fun <TResponse> registerScoreboardStorageResponseHandler(
            responseType: ScoreboardStorageFileResponseS2CPacket.Type<TResponse>,
            responseFutureMap: MutableMap<UUID, CompletableFuture<TResponse>>,
        ) {
            ClientPlayNetworking.registerGlobalReceiver(responseType.id) { payload, _ ->
                responseFutureMap.remove(payload.requestId)?.complete(payload.params)
            }
        }
    }

    private val commandDispatcherFactory: (DynamicRegistryManager) -> CommandDispatcher<CommandSource> = { registryManager: DynamicRegistryManager ->
        CommandDispatcher(initializePacket.commandTree.getCommandTree(CommandRegistryAccess.of(registryManager, client.networkHandler?.enabledFeatures)))
    }.memoizeLast()

    override val dynamicRegistryManager: DynamicRegistryManager
        get() = receivedRegistryManager ?: ClientCommandCrafter.getLoadedClientsideRegistries().combinedRegistries.combinedRegistryManager
    override val commandDispatcher
        get() = commandDispatcherFactory(dynamicRegistryManager)
    override val functionPermissionLevel = initializePacket.functionPermissionLevel
    override val serverLog =
        if(client.networkHandler?.run { (connection as ClientConnectionAccessor).channel } is LocalChannel) {
            null
        } else {
            NetworkServerLog()
        }

    override val commandExecutor = NetworkCommandExecutor(client)
    override val debugService = object : ServerDebugConnectionService {
        override fun setupEditorDebugConnection(editorDebugConnection: EditorDebugConnection) {
            getOrCreateDebugConnectionId(editorDebugConnection)
        }

        override fun setBreakpoints(
            breakpoints: Array<UnparsedServerBreakpoint>,
            source: Source,
            fileType: PackContentFileType,
            id: PackagedId,
            editorDebugConnection: EditorDebugConnection,
        ): CompletableFuture<SetBreakpointsResponse> {
            val debugConnectionId = getOrCreateDebugConnectionId(editorDebugConnection)
            val completableFuture = CompletableFuture<SetBreakpointsResponse>()
            val requestId = UUID.randomUUID()
            currentBreakpointRequests[requestId] = {
                for(breakpoint in it) {
                    if(breakpoint.source == null) {
                        breakpoint.source = source
                    }
                }
                val setBreakpointsDebuggerResponse = SetBreakpointsResponse()
                setBreakpointsDebuggerResponse.breakpoints = it
                completableFuture.complete(setBreakpointsDebuggerResponse)
            }
            ClientPlayNetworking.send(
                SetBreakpointsRequestC2SPacket(breakpoints, fileType, id, source, requestId, debugConnectionId)
            )
            return completableFuture
        }

        override fun retrieveSourceReference(sourceReference: Int, editorDebugConnection: EditorDebugConnection): CompletableFuture<SourceResponse?> {
            val debugConnectionId = getOrCreateDebugConnectionId(editorDebugConnection)
            val completableFuture = CompletableFuture<SourceResponse?>()
            val requestId = UUID.randomUUID()
            currentSourceReferenceRequests[requestId] = completableFuture
            ClientPlayNetworking.send(
                SourceReferenceRequestC2SPacket(sourceReference, requestId, debugConnectionId)
            )
            return completableFuture
        }

        override fun removeEditorDebugConnection(editorDebugConnection: EditorDebugConnection) {
            val id = editorDebugConnections.remove(editorDebugConnection) ?: return
            ClientPlayNetworking.send(
                EditorDebugConnectionRemovedC2SPacket(id)
            )
        }

        private fun getOrCreateDebugConnectionId(editorDebugConnection: EditorDebugConnection): UUID {
            return editorDebugConnections.computeIfAbsent(editorDebugConnection) {
                val id = UUID.randomUUID()
                ClientPlayNetworking.send(
                    DebugConnectionRegistrationC2SPacket(it.oneTimeDebugTarget, it.nextSourceReference, it.suspendServer, id)
                )
                it.lifecycle.configurationDoneEvent.thenRun {
                    ClientPlayNetworking.send(
                        ConfigurationDoneC2SPacket(id)
                    )
                }
                id
            }
        }
    }
    override val contextCompletionProvider = object : ContextCompletionProvider {
        override fun getCompletions(fullInput: DirectiveStringReader<AnalyzingResourceCreator>): CompletableFuture<List<CompletionItem>> {
            val future = CompletableFuture<List<CompletionItem>>()
            val requestId = UUID.randomUUID()
            currentContextCompletionRequests[requestId] = future
            ClientPlayNetworking.send(
                ContextCompletionRequestC2SPacket(requestId, fullInput.lines, fullInput.cursorMapper.mapToSource(fullInput.skippingCursor))
            )
            return future
        }
    }

    override val datapackReloader = {
        ClientPlayNetworking.send(ReloadDatapacksC2SPacket)
    }

    override fun createScoreboardStorageFileSystem(): NetworkScoreboardStorageFileSystem {
        val fileSystem = NetworkScoreboardStorageFileSystem(UUID.randomUUID())
        scoreboardStorageFileSystems[fileSystem.fileSystemId] = fileSystem
        return fileSystem
    }

    class NetworkServerLog : Log {
        val log = SizeLimitedCallbackLinkedBlockingQueue<String>()
        override val name
            get() = DirectServerConnection.SERVER_LOG_CHANNEL
        override fun addMessageCallback(callback: SizeLimitedCallbackLinkedBlockingQueue.Callback<String>) {
            log.addCallback(callback)
        }
    }

    class NetworkCommandExecutor(private val client: MinecraftClient) : CommandExecutor {
        override fun executeCommand(command: String) {
            client.inGameHud.chatHud.addToMessageHistory("/$command") //TODO: Test how multiple lines work in chat screen
            client.networkHandler?.sendChatCommand(command)
        }
    }

    inner class NetworkScoreboardStorageFileSystem(val fileSystemId: UUID) : ScoreboardStorageFileSystem {
        var currentOnDidChangeFileCallback: ((Array<FileEvent>) -> Unit)? = null
        override fun setOnDidChangeFileCallback(callback: (Array<FileEvent>) -> Unit) {
            currentOnDidChangeFileCallback = callback
        }

        override fun watch(params: FileSystemWatchParams) {
            ClientPlayNetworking.send(ScoreboardStorageFileNotificationC2SPacket.ADD_WATCH_PACKET.factory(fileSystemId, params))
        }

        override fun removeWatch(params: FileSystemRemoveWatchParams) {
            ClientPlayNetworking.send(ScoreboardStorageFileNotificationC2SPacket.REMOVE_WATCH_PACKET.factory(fileSystemId, params))
        }

        override fun stat(params: UriParams): CompletableFuture<FileSystemResult<FileStat>> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<FileSystemResult<FileStat>>()
            currentScoreboardStorageStatRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.STAT_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun readDirectory(params: UriParams): CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>>()
            currentScoreboardStorageReadDirectoryRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.READ_DIRECTORY_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun createDirectory(params: UriParams): CompletableFuture<FileSystemResult<Unit>> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<FileSystemResult<Unit>>()
            currentScoreboardStorageCreateDirectoryRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.CREATE_DIRECTORY_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun readFile(params: UriParams): CompletableFuture<FileSystemResult<ReadFileResult>> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<FileSystemResult<ReadFileResult>>()
            currentScoreboardStorageReadFileRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.READ_FILE_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun writeFile(params: WriteFileParams): CompletableFuture<FileSystemResult<Unit>> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<FileSystemResult<Unit>>()
            currentScoreboardStorageWriteFileRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.WRITE_FILE_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun delete(params: DeleteParams): CompletableFuture<FileSystemResult<Unit>> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<FileSystemResult<Unit>>()
            currentScoreboardStorageDeleteRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.DELETE_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun rename(params: RenameParams): CompletableFuture<FileSystemResult<Unit>> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<FileSystemResult<Unit>>()
            currentScoreboardStorageRenameRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.RENAME_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun getLoadableStorageNamespaces(params: Unit): CompletableFuture<LoadableStorageNamespaces> {
            val requestId = UUID.randomUUID()
            val future = CompletableFuture<LoadableStorageNamespaces>()
            currentScoreboardStorageLoadableStorageNamespacesRequests[requestId] = future
            ClientPlayNetworking.send(ScoreboardStorageFileRequestC2SPacket.LOADABLE_STORAGE_NAMESPACES_PACKET.factory(fileSystemId, requestId, params))
            return future
        }

        override fun loadStorageNamespace(params: LoadStorageNamespaceParams) {
            ClientPlayNetworking.send(ScoreboardStorageFileNotificationC2SPacket.LOAD_STORAGE_NAMESPACE_PACKET.factory(fileSystemId, params))
        }
    }

    class ServerConnectionNotSupportedException(message: String?) : Exception(message) {
        constructor() : this(null)
    }
}