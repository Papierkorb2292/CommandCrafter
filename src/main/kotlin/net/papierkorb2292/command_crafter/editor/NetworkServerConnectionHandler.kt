package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.RootCommandNode
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.command.CommandSource
import net.minecraft.loot.LootDataType
import net.minecraft.nbt.NbtOps
import net.minecraft.network.ClientConnection
import net.minecraft.network.listener.ClientCommonPacketListener
import net.minecraft.network.packet.CustomPayload
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket
import net.minecraft.registry.*
import net.minecraft.registry.tag.TagPacketSerializer
import net.minecraft.resource.ResourcePackManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.world.SaveProperties
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.debugger.helper.ReservedBreakpointIdStart
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.helper.SizeLimitedCallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ReloadCommandAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.SerializableRegistriesAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.ServerRecipeManagerAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.TagPacketSerializerSerializedAccessor
import net.papierkorb2292.command_crafter.networking.packets.*
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileNotificationC2SPacket
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileNotificationS2CPacket
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileRequestC2SPacket
import net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem.ScoreboardStorageFileResponseS2CPacket
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.stream.Collectors

object NetworkServerConnectionHandler {
    val currentBreakpointIdsRequests: MutableMap<UUID, CompletableFuture<ReservedBreakpointIdStart>> = mutableMapOf()

    val ALL_DYNAMIC_REGISTRIES = RegistryLoader.DYNAMIC_REGISTRIES + LootDataType.stream().map {
        createRegistryLoaderEntryForLootDataType(it)
    }.toList()
    val SYNCED_REGISTRIES = ALL_DYNAMIC_REGISTRIES + RegistryLoader.DIMENSION_REGISTRIES

    val SYNCED_REGISTRY_KEYS = SYNCED_REGISTRIES.mapTo(mutableSetOf()) { it.key }

    private val currentConnections = mutableMapOf<ServerPlayNetworkHandler, DirectServerConnection>()

    private val scoreboardStorageWriteFileCombiner = PartialSequenceCombiner(PartialWriteFileParams::isLastPart, WriteFileParams::fromPartial)

    private val editorDebugConnections = mutableMapOf<ServerPlayNetworkHandler, MutableMap<UUID, ServerNetworkDebugConnection>>()
    private val serverDebugPauses: MutableMap<UUID, ServerNetworkDebugConnection.DebugPauseInformation> = mutableMapOf()

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

    private fun isPlayerAllowedConnection(player: ServerPlayerEntity) =
        player.hasPermissionLevel(2)

    fun registerPacketHandlers() {
        // Don't use registerAsyncServerPacketHandler here, because the client wouldn't be able to check whether a handler is registered for the packet
        ServerPlayNetworking.registerGlobalReceiver(RequestNetworkServerConnectionC2SPacket.ID) handler@{ payload, context ->
            if(!isPlayerAllowedConnection(context.player())) {
                context.responseSender().sendPacket(
                    InitializeNetworkServerConnectionS2CPacket(
                        false,
                        "insufficient permissions",
                        CommandTreeS2CPacket(RootCommandNode()),
                        0,
                        payload.requestId
                    )
                )
                return@handler
            }
            if(payload.clientModVersion != CommandCrafter.VERSION) {
                context.responseSender().sendPacket(
                    InitializeNetworkServerConnectionS2CPacket(
                        false,
                        "mismatched mod version (client=${payload.clientModVersion},server=${CommandCrafter.VERSION})",
                        CommandTreeS2CPacket(RootCommandNode()),
                        0,
                        payload.requestId
                    )
                )
                return@handler
            }

            val connection = DirectServerConnection(context.player().server)
            currentConnections[context.player().networkHandler] = connection

            sendConnectionRequestResponse(
                context.player().server,
                payload,
                connection,
                context.responseSender(),
                context.player().networkHandler
            )

            connection.serverLog?.addMessageCallback(
                object : SizeLimitedCallbackLinkedBlockingQueue.Callback<String> {
                    override fun onElementAdded(e: String) {
                        context.responseSender().sendPacket(LogMessageS2CPacket(e))
                    }

                    override fun shouldRemoveCallback() = context.player().isDisconnected
                }
            )
        }
        registerAsyncServerPacketHandler(SetBreakpointsRequestC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.networkHandler] ?: return@registerAsyncServerPacketHandler
            val debugConnection = editorDebugConnections[context.player.networkHandler]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
            serverConnection.debugService.setBreakpoints(
                payload.breakpoints,
                payload.source,
                payload.fileType,
                payload.id,
                debugConnection
            ).thenAccept {
                context.sendPacket(SetBreakpointsResponseS2CPacket(it.breakpoints, payload.requestId))
            }
        }
        registerAsyncServerPacketHandler(EditorDebugConnectionRemovedC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.networkHandler] ?: return@registerAsyncServerPacketHandler
            val debugConnection = editorDebugConnections[context.player.networkHandler]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
            serverConnection.debugService.removeEditorDebugConnection(debugConnection)
        }
        registerAsyncServerPacketHandler(SourceReferenceRequestC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.networkHandler] ?: return@registerAsyncServerPacketHandler
            val debugConnection = editorDebugConnections[context.player.networkHandler]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
            serverConnection.debugService.retrieveSourceReference(payload.sourceReference, debugConnection).thenAccept {
                context.sendPacket(SourceReferenceResponseS2CPacket(it, payload.requestId))
            }
        }
        registerAsyncServerPacketHandler(ReserveBreakpointIdsResponseC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            currentBreakpointIdsRequests.remove(payload.requestId)?.complete(payload.start)
        }
        registerAsyncServerPacketHandler(ConfigurationDoneC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val debugConnection = editorDebugConnections[context.player.networkHandler]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
            debugConnection.lifecycle.configurationDoneEvent.complete(null)
        }
        // This is async, so it isn't delayed until the next server tick, which sometimes caused "setBreakpoints" to be run first, which created its own DebugConnection
        registerAsyncServerPacketHandler(DebugConnectionRegistrationC2SPacket.ID) { payload, context->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.networkHandler] ?: return@registerAsyncServerPacketHandler
            val debugConnection = ServerNetworkDebugConnection(context.player, payload.debugConnectionId, payload.oneTimeDebugTarget, payload.nextSourceReference, payload.suspendServer)
            editorDebugConnections.getOrPut(context.player.networkHandler, ::mutableMapOf).putIfAbsent(payload.debugConnectionId, debugConnection)
            serverConnection.debugService.setupEditorDebugConnection(debugConnection)
        }
        registerAsyncServerPacketHandler(ScoreboardStorageFileNotificationC2SPacket.ADD_WATCH_PACKET.id) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val fileSystem = getServerScoreboardStorageFileSystem(context.player, payload.fileSystemId) ?: return@registerAsyncServerPacketHandler
            fileSystem.watch(payload.params)
        }
        registerAsyncServerPacketHandler(ScoreboardStorageFileNotificationC2SPacket.REMOVE_WATCH_PACKET.id) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val fileSystem = getServerScoreboardStorageFileSystem(context.player, payload.fileSystemId) ?: return@registerAsyncServerPacketHandler
            fileSystem.removeWatch(payload.params)
        }
        registerScoreboardStorageRequestHandler(
            ScoreboardStorageFileRequestC2SPacket.STAT_PACKET,
            ScoreboardStorageFileResponseS2CPacket.STAT_RESPONSE_PACKET,
            ScoreboardStorageFileSystem::stat
        )
        registerScoreboardStorageRequestHandler(
            ScoreboardStorageFileRequestC2SPacket.READ_DIRECTORY_PACKET,
            ScoreboardStorageFileResponseS2CPacket.READ_DIRECTORY_RESPONSE_PACKET,
            ScoreboardStorageFileSystem::readDirectory
        )
        registerScoreboardStorageRequestHandler(
            ScoreboardStorageFileRequestC2SPacket.CREATE_DIRECTORY_PACKET,
            ScoreboardStorageFileResponseS2CPacket.CREATE_DIRECTORY_RESPONSE_PACKET,
            ScoreboardStorageFileSystem::createDirectory
        )
        // Can't use registerScoreboardStorageRequestHandler because requests can be spread across multiple packets when they're too big
        registerAsyncServerPacketHandler(ScoreboardStorageFileRequestC2SPacket.READ_FILE_PACKET.id) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val fileSystem = getServerScoreboardStorageFileSystem(context.player, payload.fileSystemId) ?: return@registerAsyncServerPacketHandler
            fileSystem.readFile(payload.params).thenAccept { readResult ->
                when(readResult.type) {
                    FileSystemResult.ResultType.FILE_NOT_FOUND_ERROR ->
                        context.sendPacket(ScoreboardStorageFileResponseS2CPacket.READ_FILE_RESPONSE_PACKET.factory(
                                payload.requestId,
                                FileSystemResult(readResult.fileNotFoundError!!)
                            ))
                    FileSystemResult.ResultType.SUCCESS -> readResult.result!!.toPartial().forEach {
                        context.sendPacket(ScoreboardStorageFileResponseS2CPacket.READ_FILE_RESPONSE_PACKET.factory(
                            payload.requestId,
                            FileSystemResult(it)
                        ))
                    }
                }
            }
        }
        registerAsyncServerPacketHandler(ScoreboardStorageFileRequestC2SPacket.WRITE_FILE_PACKET.id) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val fileSystem = getServerScoreboardStorageFileSystem(context.player, payload.fileSystemId) ?: return@registerAsyncServerPacketHandler
            val combined = scoreboardStorageWriteFileCombiner.consumePartial(payload.params, payload.requestId) ?: return@registerAsyncServerPacketHandler
            fileSystem.writeFile(combined).thenAccept {
                context.sendPacket(ScoreboardStorageFileResponseS2CPacket.WRITE_FILE_RESPONSE_PACKET.factory(payload.requestId, it))
            }
        }
        registerScoreboardStorageRequestHandler(
            ScoreboardStorageFileRequestC2SPacket.DELETE_PACKET,
            ScoreboardStorageFileResponseS2CPacket.DELETE_RESPONSE_PACKET,
            ScoreboardStorageFileSystem::delete
        )
        registerScoreboardStorageRequestHandler(
            ScoreboardStorageFileRequestC2SPacket.RENAME_PACKET,
            ScoreboardStorageFileResponseS2CPacket.RENAME_RESPONSE_PACKET,
            ScoreboardStorageFileSystem::rename
        )
        registerScoreboardStorageRequestHandler(
            ScoreboardStorageFileRequestC2SPacket.LOADABLE_STORAGE_NAMESPACES_PACKET,
            ScoreboardStorageFileResponseS2CPacket.LOADABLE_STORAGE_NAMESPACES_RESPONSE_PACKET,
            ScoreboardStorageFileSystem::getLoadableStorageNamespaces
        )
        registerAsyncServerPacketHandler(ScoreboardStorageFileNotificationC2SPacket.LOAD_STORAGE_NAMESPACE_PACKET.id) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val fileSystem = getServerScoreboardStorageFileSystem(context.player, payload.fileSystemId) ?: return@registerAsyncServerPacketHandler
            fileSystem.loadStorageNamespace(payload.params)
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
                context.sendPacket(GetVariablesResponseS2CPacket(payload.requestId, it))
            }
        }
        registerAsyncServerPacketHandler(SetVariableRequestC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerAsyncServerPacketHandler
            debugPause.pauseContext.setVariable(payload.args).thenAccept {
                context.sendPacket(SetVariableResponseS2CPacket(payload.requestId, it))
            }
        }
        registerAsyncServerPacketHandler(StepInTargetsRequestC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerAsyncServerPacketHandler
            debugPause.actions.stepInTargets(payload.frameId).thenAccept {
                context.sendPacket(StepInTargetsResponseS2CPacket(payload.requestId, it))
            }
        }
        registerAsyncServerPacketHandler(ContextCompletionRequestC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.networkHandler] ?: return@registerAsyncServerPacketHandler
            val server = context.player.server
            @Suppress("UNCHECKED_CAST")
            val reader = DirectiveStringReader(FileMappingInfo(payload.inputLines), server.commandManager.dispatcher as CommandDispatcher<CommandSource>, AnalyzingResourceCreator(null, ""))
            reader.cursor = payload.cursor
            serverConnection.contextCompletionProvider.getCompletions(reader).thenAccept {
                context.sendPacket(ContextCompletionResponseS2CPacket(payload.requestId, it))
            }
        }
        registerAsyncServerPacketHandler(ReloadDatapacksC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.networkHandler] ?: return@registerAsyncServerPacketHandler
            serverConnection.datapackReloader()
        }

        ServerPlayConnectionEvents.DISCONNECT.register { networkHandler, _ ->
            val serverConnection = currentConnections.remove(networkHandler) ?: return@register
            editorDebugConnections.remove(networkHandler)?.values?.forEach {
                serverConnection.debugService.removeEditorDebugConnection(it)
            }
            ServerScoreboardStorageFileSystem.createdFileSystems.remove(networkHandler)
        }
    }

    private fun <T> createRegistryLoaderEntryForLootDataType(dataType: LootDataType<T>) =
        RegistryLoader.Entry(dataType.registryKey, dataType.codec, false)

    private fun sendConnectionRequestResponse(
        server: MinecraftServer,
        requestPacket: RequestNetworkServerConnectionC2SPacket,
        connection: DirectServerConnection,
        packetSender: PacketSender,
        networkHandler: ServerPlayNetworkHandler,
    ) {
        sendDynamicRegistries(server, networkHandler)

        val responsePacket = InitializeNetworkServerConnectionS2CPacket(
            true,
            null,
            CommandTreeS2CPacket(connection.commandDispatcher.root),
            server.functionPermissionLevel,
            requestPacket.requestId
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
        networkHandler: ServerPlayNetworkHandler,
    ) {
        // Only send to players that have CommandCrafter installed and are connected
        if(networkHandler !in currentConnections)
            return

        // Can be cast to this type, because that is the value assigned in the DataPackContents constructor
        val registryManager = server.reloadableRegistries.createRegistryLookup() as DynamicRegistryManager

        val tagWrapperLookup = (server.recipeManager as ServerRecipeManagerAccessor).registries

        val serializedRegistriesTags = serializeTags(tagWrapperLookup, registryManager)
        // Sync tags of non-dynamic registries first, because
        // client builds registry manager once all SYNCED_REGISTRIES have been received
        registryManager.streamAllRegistryKeys().forEach {
            if(it in SYNCED_REGISTRY_KEYS) return@forEach
            val registryTags = serializedRegistriesTags[it] ?: return@forEach
            networkHandler.sendPacket(
                CustomPayloadS2CPacket(
                    CommandCrafterDynamicRegistryS2CPacket(DynamicRegistriesS2CPacket(it, emptyList()), registryTags)
                )
            )
        }
        SYNCED_REGISTRIES.forEach {
            sendDynamicRegistry(registryManager, it, networkHandler, serializedRegistriesTags[it.key])
        }
    }

    private fun serializeTags(
        tagWrapperLookup: RegistryWrapper.WrapperLookup,
        entryLookup: DynamicRegistryManager,
    ): Map<RegistryKey<out Registry<*>>, TagPacketSerializer.Serialized> {
        return tagWrapperLookup.streamAllRegistryKeys().map {
            val serializedTags = mutableMapOf<Identifier, IntList>()
            val tagRegistry = tagWrapperLookup.getOrThrow(it)
            val entryRegistry = entryLookup.getOrThrow(it)
            for(tag in tagRegistry.tags.toList()) {
                val serialized = IntArrayList(tag.size())
                for(entry in tag) {
                    val id = entry.key.orElseThrow { IllegalArgumentException("Synced tag entries must have an id") }
                    serialized.add(entryRegistry.getRawId(entryRegistry.get(id)))
                }
                serializedTags[tag.tag.id] = serialized
            }
            it to TagPacketSerializerSerializedAccessor.callInit(serializedTags)
        }.collect(Collectors.toMap({ it.first }, { it.second }))
    }

    private fun sendDynamicRegistry(
        registryManager: DynamicRegistryManager,
        registry: RegistryLoader.Entry<*>,
        networkHandler: ServerPlayNetworkHandler,
        registryTags: TagPacketSerializer.Serialized?,
    ) {
        SerializableRegistriesAccessor.callSerialize(
            registryManager.getOps(NbtOps.INSTANCE),
            registry,
            registryManager,
            emptySet()
        ) { registryKey, entries ->
            networkHandler.sendPacket(
                CustomPayloadS2CPacket(
                    CommandCrafterDynamicRegistryS2CPacket(DynamicRegistriesS2CPacket(registryKey, entries), registryTags)
                )
            )
        }
    }

    private fun <TParams, TResult> registerScoreboardStorageRequestHandler(
        requestType: ScoreboardStorageFileRequestC2SPacket.Type<TParams>,
        responseType: ScoreboardStorageFileResponseS2CPacket.Type<TResult>,
        handler: (ScoreboardStorageFileSystem, TParams) -> CompletableFuture<TResult>,
    ) {
        registerAsyncServerPacketHandler(requestType.id) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val fileSystem = getServerScoreboardStorageFileSystem(context.player, payload.fileSystemId) ?: return@registerAsyncServerPacketHandler
            handler(fileSystem, payload.params).thenAccept {
                context.sendPacket(responseType.factory(payload.requestId, it))
            }
        }
    }

    private fun getServerScoreboardStorageFileSystem(player: ServerPlayerEntity, id: UUID): ServerScoreboardStorageFileSystem? {
        val serverConnection = currentConnections[player.networkHandler] ?: return null
        return ServerScoreboardStorageFileSystem.createdFileSystems.getOrPut(player.networkHandler, ::mutableMapOf).getOrPut(id) {
            val fileSystem = serverConnection.createScoreboardStorageFileSystem()
            fileSystem.setOnDidChangeFileCallback {
                player.networkHandler.sendPacket(
                    CustomPayloadS2CPacket(ScoreboardStorageFileNotificationS2CPacket.DID_CHANGE_FILE_PACKET.factory(id, it))
                )
            }
            fileSystem
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