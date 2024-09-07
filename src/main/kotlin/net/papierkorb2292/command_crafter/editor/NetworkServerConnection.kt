package net.papierkorb2292.command_crafter.editor

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Maps
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.tree.RootCommandNode
import io.netty.channel.local.LocalChannel
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.nbt.NbtOps
import net.minecraft.network.ClientConnection
import net.minecraft.network.listener.ClientCommonPacketListener
import net.minecraft.network.packet.CustomPayload
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket
import net.minecraft.registry.*
import net.minecraft.resource.ResourceFactory
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.console.PreLaunchLogListener
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkDebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkVariablesReferencer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.ReservedBreakpointIdStart
import net.papierkorb2292.command_crafter.editor.debugger.helper.getDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.helper.setupOneTimeDebugTarget
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.SizeLimitedCallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.mixin.editor.ClientConnectionAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.SerializableRegistriesAccessor
import net.papierkorb2292.command_crafter.networking.packets.*
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.collections.set

class NetworkServerConnection private constructor(private val client: MinecraftClient, private val initializePacket: InitializeNetworkServerConnectionS2CPacket) : MinecraftServerConnection {
    companion object {
        const val SERVER_LOG_CHANNEL = "server"

        private val asyncServerPacketHandlers = mutableMapOf<CustomPayload.Id<*>, AsyncPacketHandler<*, AsyncC2SPacketContext>>()
        private val asyncServerPacketHandlerExecutor = Executors.newSingleThreadExecutor()
        fun <TPayload : CustomPayload> registerAsyncServerPacketHandler(id: CustomPayload.Id<TPayload>, handler: AsyncPacketHandler<TPayload, AsyncC2SPacketContext>) {
            asyncServerPacketHandlers[id] = handler
        }
        fun <TPayload: CustomPayload> callPacketHandler(packet: TPayload, context: AsyncC2SPacketContext): Boolean {
            val handler = asyncServerPacketHandlers[packet.id] ?: return false
            asyncServerPacketHandlerExecutor.execute {
                @Suppress("UNCHECKED_CAST")
                (handler as AsyncPacketHandler<TPayload, AsyncC2SPacketContext>).receive(packet, context)
            }
            return true
        }

        val currentGetVariablesRequests: MutableMap<UUID, CompletableFuture<Array<Variable>>> = mutableMapOf()
        val currentSetVariableRequests: MutableMap<UUID, CompletableFuture<VariablesReferencer.SetVariableResult?>> = mutableMapOf()
        val currentStepInTargetsRequests: MutableMap<UUID, CompletableFuture<StepInTargetsResponse>> = mutableMapOf()
        val currentSourceReferenceRequests: MutableMap<UUID, CompletableFuture<SourceResponse?>> = mutableMapOf()
        val currentBreakpointIdsRequests: MutableMap<UUID, CompletableFuture<ReservedBreakpointIdStart>> = mutableMapOf()
        val currentContextCompletionRequests: MutableMap<UUID, CompletableFuture<Suggestions>> = mutableMapOf()

        private var currentConnectionRequest: Pair<UUID, CompletableFuture<NetworkServerConnection>>? = null
        private val currentBreakpointRequests: MutableMap<UUID, (Array<Breakpoint>) -> Unit> = Maps.newHashMap()

        private val clientEditorDebugConnections: BiMap<EditorDebugConnection, UUID> = HashBiMap.create()
        private val serverEditorDebugConnections: MutableMap<UUID, ServerNetworkDebugConnection> = ConcurrentHashMap()
        private val serverDebugPauses: MutableMap<UUID, ServerNetworkDebugConnection.DebugPauseInformation> = mutableMapOf()

        private val receivedRegistries = mutableMapOf<RegistryKey<out Registry<*>>, List<SerializableRegistries.SerializedRegistryEntry>>()
        private var receivedRegistryManager: DynamicRegistryManager? = null

        private val currentConnections = mutableListOf<NetworkServerConnection>()

        fun isPlayerAllowedConnection(player: ServerPlayerEntity) =
            player.hasPermissionLevel(2)

        fun requestAndCreate(): CompletableFuture<NetworkServerConnection> {
            if(!ClientPlayNetworking.canSend(RequestNetworkServerConnectionC2SPacket.ID)) {
                return CompletableFuture.failedFuture(ServerConnectionNotSupportedException("Server doesn't support editor connections"))
            }
            val requestId = UUID.randomUUID()
            val result = CompletableFuture<NetworkServerConnection>()
            currentConnectionRequest = requestId to result
            ClientPlayNetworking.send(RequestNetworkServerConnectionC2SPacket(requestId))
            return result
        }

        fun registerClientPacketHandlers() {
            ClientPlayNetworking.registerGlobalReceiver(InitializeNetworkServerConnectionS2CPacket.ID) handler@{ payload, context ->
                val currentRequest = currentConnectionRequest ?: return@handler
                if(payload.requestId != currentRequest.first) return@handler
                if(!payload.successful) {
                    currentRequest.second.completeExceptionally(ServerConnectionNotSupportedException("Server didn't permit editor connection"))
                    return@handler
                }
                val connection = NetworkServerConnection(context.client(), payload)
                currentConnections += connection
                currentRequest.second.complete(connection)
            }
            ClientPlayNetworking.registerGlobalReceiver(CommandCrafterDynamicRegistryS2CPacket.ID) { payload, _ ->
                receivedRegistries[payload.dynamicRegistry.registry] = payload.dynamicRegistry.entries
                if(RegistryLoader.DYNAMIC_REGISTRIES.all { receivedRegistries.containsKey(it.key) }
                    && RegistryLoader.DIMENSION_REGISTRIES.all { receivedRegistries.containsKey(it.key) }) {
                    //All registries have been received
                    val dynamicRegistries = RegistryLoader.loadFromNetwork(
                        receivedRegistries,
                        ResourceFactory.MISSING, //No resource loading is required, because no common packs were specified
                        CommandCrafter.defaultDynamicRegistryManager.combinedRegistryManager,
                        RegistryLoader.DYNAMIC_REGISTRIES + RegistryLoader.DIMENSION_REGISTRIES
                    )
                    val initialRegistries = ServerDynamicRegistryType.createCombinedDynamicRegistries();
                    receivedRegistryManager = initialRegistries.with(
                        ServerDynamicRegistryType.RELOADABLE,
                        dynamicRegistries
                    ).combinedRegistryManager
                    receivedRegistries.clear()
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
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.popStackFrames(payload.amount)
            }
            ClientPlayNetworking.registerGlobalReceiver(PushStackFramesS2CPacket.ID) { payload, _ ->
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.pushStackFrames(payload.stackFrames)
            }
            ClientPlayNetworking.registerGlobalReceiver(PausedUpdateS2CPacket.ID) handler@{ payload, context ->
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@handler
                val pause = payload.pause
                if(pause == null) editorConnection.pauseEnded()
                else editorConnection.pauseStarted(
                    NetworkDebugPauseActions(context.responseSender(), pause.first),
                    pause.second,
                    NetworkVariablesReferencer(context.responseSender(), pause.first)
                )
            }
            ClientPlayNetworking.registerGlobalReceiver(UpdateReloadedBreakpointS2CPacket.ID) { payload, _ ->
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.updateReloadedBreakpoint(payload.update)
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
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.output(payload.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(SourceReferenceResponseS2CPacket.ID) { payload, _ ->
                currentSourceReferenceRequests.remove(payload.requestId)?.complete(payload.source)
            }
            ClientPlayNetworking.registerGlobalReceiver(ReserveBreakpointIdsRequestS2CPacket.ID) { payload, context ->
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.reserveBreakpointIds(payload.count)?.thenAccept {
                    context.responseSender().sendPacket(
                        ReserveBreakpointIdsResponseC2SPacket(it, payload.requestId)
                    )
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(DebuggerExitS2CPacket.ID) { payload, _ ->
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.lifecycle.shouldExitEvent.complete(payload.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(SourceReferenceAddedS2CPacket.ID) { payload, _ ->
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.onSourceReferenceAdded()
            }
            ClientPlayNetworking.registerGlobalReceiver(ContextCompletionResponseS2CPacket.ID) { payload, _ ->
                currentContextCompletionRequests.remove(payload.requestId)?.complete(payload.asSuggestions())
            }
            ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
                clientEditorDebugConnections.clear()
                currentConnections.clear()
            }
        }

        fun registerServerPacketHandlers() {
            ServerPlayNetworking.registerGlobalReceiver(RequestNetworkServerConnectionC2SPacket.ID) handler@{ payload, context ->
                if(!isPlayerAllowedConnection(context.player())) {
                    context.responseSender().sendPacket(    
                        InitializeNetworkServerConnectionS2CPacket(
                            false,
                            CommandTreeS2CPacket(RootCommandNode()),
                            0,
                            payload.requestId
                        )
                    )
                    return@handler
                }
                sendConnectionRequestResponse(context.player().server, payload, context.responseSender(), context.player().networkHandler)

                if(context.player().server.isDedicated) {
                    startSendingLogMessages(context.responseSender(), context.player())
                }
            }
            registerAsyncServerPacketHandler(SetBreakpointsRequestC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val server = context.player.server
                val debugManager = server.getDebugManager()
                context.sendPacket(
                    SetBreakpointsResponseS2CPacket(debugManager.setBreakpoints(
                        payload.breakpoints,
                        payload.fileType,
                        payload.id,
                        context.player,
                        serverEditorDebugConnections.computeIfAbsent(payload.debugConnectionId) {
                            ServerNetworkDebugConnection(context.player, it)
                        },
                        payload.sourceReference
                    ), payload.requestId)
                )
            }
            registerAsyncServerPacketHandler(EditorDebugConnectionRemovedC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val debugConnection = serverEditorDebugConnections.remove(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
                debugConnection.currentPauseId?.run {
                    serverDebugPauses.remove(this)?.actions?.continue_()
                }
                val server = context.player.server
                val debugManager = server.getDebugManager()
                server.execute { debugManager.removeDebugConnection(debugConnection) }
            }
            registerAsyncServerPacketHandler(DebugPauseActionC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerAsyncServerPacketHandler
                payload.action.apply(debugPause.actions, payload)
            }
            registerAsyncServerPacketHandler(GetVariablesRequestC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerAsyncServerPacketHandler
                debugPause.pauseContext.getVariables(payload.args).thenAccept {
                    context.sendPacket(
                        GetVariablesResponseS2CPacket(payload.requestId, it)
                    )
                }
            }
            registerAsyncServerPacketHandler(SetVariableRequestC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerAsyncServerPacketHandler
                debugPause.pauseContext.setVariable(payload.args).thenAccept {
                    context.sendPacket(
                        SetVariableResponseS2CPacket(payload.requestId, it)
                    )
                }
            }
            registerAsyncServerPacketHandler(StepInTargetsRequestC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerAsyncServerPacketHandler
                debugPause.actions.stepInTargets(payload.frameId).thenAccept {
                    context.sendPacket(
                        StepInTargetsResponseS2CPacket(payload.requestId, it)
                    )
                }
            }
            registerAsyncServerPacketHandler(SourceReferenceRequestC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val debugConnection = serverEditorDebugConnections[payload.debugConnectionId] ?: return@registerAsyncServerPacketHandler
                val server = context.player.server
                val debugManager = server.getDebugManager()
                val sourceResponse = debugManager.retrieveSourceReference(debugConnection, payload.sourceReference)
                context.sendPacket(
                    SourceReferenceResponseS2CPacket(sourceResponse, payload.requestId)
                )
            }
            registerAsyncServerPacketHandler(ReserveBreakpointIdsResponseC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                currentBreakpointIdsRequests.remove(payload.requestId)?.complete(payload.start)
            }
            ServerPlayNetworking.registerGlobalReceiver(ConfigurationDoneC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player())) return@registerGlobalReceiver
                val debugConnection = serverEditorDebugConnections[payload.debugConnectionId] ?: return@registerGlobalReceiver
                debugConnection.lifecycle.configurationDoneEvent.complete(null)
            }
            // This is async, so it isn't delayed until the next server tick, which sometimes caused "setBreakpoints" to be run first, which created its own DebugConnection
            registerAsyncServerPacketHandler(DebugConnectionRegistrationC2SPacket.ID) { payload, context->
                if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
                val debugConnection = ServerNetworkDebugConnection(context.player, payload.debugConnectionId, payload.oneTimeDebugTarget, payload.nextSourceReference, payload.suspendServer)
                serverEditorDebugConnections.putIfAbsent(payload.debugConnectionId, debugConnection)
                debugConnection.setupOneTimeDebugTarget(context.player.server)
            }
            ServerPlayNetworking.registerGlobalReceiver(ContextCompletionRequestC2SPacket.ID) { payload, context ->
                if(!isPlayerAllowedConnection(context.player())) return@registerGlobalReceiver
                val server = context.player().server
                @Suppress("UNCHECKED_CAST")
                val reader = DirectiveStringReader(FileMappingInfo(payload.inputLines), server.commandManager.dispatcher as CommandDispatcher<CommandSource>, AnalyzingResourceCreator(null, ""))
                server.execute {
                    val analyzingResult = AnalyzingResult(reader.fileMappingInfo, Position())
                    LanguageManager.analyse(reader, server.commandSource, analyzingResult, LanguageManager.DEFAULT_CLOSURE)
                    val completionFuture = analyzingResult.getCompletionProviderForCursor(payload.cursor)
                        ?.dataProvider?.invoke(payload.cursor)
                        ?: CompletableFuture.completedFuture(listOf())
                    completionFuture.thenAccept {
                        context.responseSender().sendPacket(ContextCompletionResponseS2CPacket(payload.requestId, it))
                    }
                }
            }

            ServerPlayConnectionEvents.DISCONNECT.register { networkHandler, server -> server.execute {
                serverEditorDebugConnections.values.removeIf {
                    if(it.networkHandler == networkHandler) {
                        server.getDebugManager().removeDebugConnection(it)
                        serverDebugPauses[it.currentPauseId]?.actions?.continue_()
                        return@removeIf true
                    }
                    return@removeIf false
                }
            }}
        }

        private fun startSendingLogMessages(
            packetSender: PacketSender,
            player: ServerPlayerEntity,
        ) {
            PreLaunchLogListener.addLogListener(object : SizeLimitedCallbackLinkedBlockingQueue.Callback<String> {
                override fun onElementAdded(e: String) {
                    packetSender.sendPacket(LogMessageS2CPacket(e))
                }

                override fun shouldRemoveCallback() = player.isDisconnected
            })
        }

        private fun sendConnectionRequestResponse(
            server: MinecraftServer,
            requestPacket: RequestNetworkServerConnectionC2SPacket,
            packetSender: PacketSender,
            networkHandler: ServerPlayNetworkHandler
        ) {
            sendDynamicRegistries(server, networkHandler)

            val rootCommandNode = limitCommandTreeForSource(server.commandManager, ServerCommandSource(
                CommandOutput.DUMMY,
                Vec3d.ZERO,
                Vec2f.ZERO,
                null,
                server.functionPermissionLevel,
                "",
                ScreenTexts.EMPTY,
                null,
                null
            ))

            val responsePacket = InitializeNetworkServerConnectionS2CPacket(
                true,
                CommandTreeS2CPacket(rootCommandNode),
                server.functionPermissionLevel,
                requestPacket.requestId,
            )

            try {
                IdArgumentTypeAnalyzer.shouldAddPackContentFileType.set(true)
                packetSender.sendPacket(responsePacket)
            } finally {
                IdArgumentTypeAnalyzer.shouldAddPackContentFileType.remove()
            }
        }

        fun sendDynamicRegistries(
            server: MinecraftServer,
            networkHandler: ServerPlayNetworkHandler
        ) {
            RegistryLoader.DYNAMIC_REGISTRIES.forEach {
                sendDynamicRegistry(server, it, networkHandler)
            }
            RegistryLoader.DIMENSION_REGISTRIES.forEach {
                sendDynamicRegistry(server, it, networkHandler)
            }
        }

        private fun sendDynamicRegistry(
            server: MinecraftServer,
            registry: RegistryLoader.Entry<*>,
            networkHandler: ServerPlayNetworkHandler
        ) {
            SerializableRegistriesAccessor.callSerialize(
                server.registryManager.getOps(NbtOps.INSTANCE),
                registry,
                server.registryManager,
                emptySet()
            ) { registryKey, entries ->
                networkHandler.sendPacket(
                    CustomPayloadS2CPacket(
                        CommandCrafterDynamicRegistryS2CPacket(DynamicRegistriesS2CPacket(registryKey, entries))
                    )
                )
            }
        }

        fun addServerDebugPause(debugPause: ServerNetworkDebugConnection.DebugPauseInformation): UUID {
            val id = UUID.randomUUID()
            serverDebugPauses[id] = debugPause
            return id
        }
        fun removeServerDebugPauseHandler(id: UUID) {
            serverDebugPauses.remove(id)
        }
    }

    private val commandDispatcherFactory: (DynamicRegistryManager) -> CommandDispatcher<CommandSource> = { registryManager: DynamicRegistryManager ->
        val root = initializePacket.commandTree.getCommandTree(CommandRegistryAccess.of(registryManager, client.networkHandler?.enabledFeatures))
        CommandCrafter.removeLiteralsStartingWithForwardsSlash(root)
        CommandDispatcher(root)
    }.memoizeLast()

    override val dynamicRegistryManager: DynamicRegistryManager
        get() = receivedRegistryManager ?: CommandCrafter.defaultDynamicRegistryManager.combinedRegistryManager
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
                SetBreakpointsRequestC2SPacket(breakpoints, fileType, id, source.sourceReference, requestId, debugConnectionId)
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
            val id = clientEditorDebugConnections.remove(editorDebugConnection) ?: return
            ClientPlayNetworking.send(
                EditorDebugConnectionRemovedC2SPacket(id)
            )
        }

        private fun getOrCreateDebugConnectionId(editorDebugConnection: EditorDebugConnection): UUID {
            return clientEditorDebugConnections.computeIfAbsent(editorDebugConnection) {
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
        override fun getCompletions(context: CommandContext<*>, fullInput: DirectiveStringReader<AnalyzingResourceCreator>): CompletableFuture<Suggestions> {
            val future = CompletableFuture<Suggestions>()
            val requestId = UUID.randomUUID()
            currentContextCompletionRequests[requestId] = future
            ClientPlayNetworking.send(
                ContextCompletionRequestC2SPacket(requestId, fullInput.lines, fullInput.absoluteCursor)
            )
            return future
        }
    }

    class NetworkServerLog : Log {
        val log = SizeLimitedCallbackLinkedBlockingQueue<String>()
        override val name
            get() = SERVER_LOG_CHANNEL
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

    class ServerConnectionNotSupportedException(message: String?) : Exception(message) {
        constructor() : this(null)
    }

    fun interface AsyncPacketHandler<TPayload, TContext> {
        fun receive(packet: TPayload, context: TContext)
    }

    data class AsyncC2SPacketContext(
        val player: ServerPlayerEntity,
        val clientConnection: ClientConnection,
    ) {
        fun sendPacket(packet: CustomPayload) {
            sendPacket(CustomPayloadS2CPacket(packet))
        }

        fun sendPacket(packet: Packet<ClientCommonPacketListener>) {
            clientConnection.send(packet)
        }
    }
}
