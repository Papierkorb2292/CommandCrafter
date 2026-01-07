package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.RootCommandNode
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import net.fabricmc.fabric.api.event.registry.DynamicRegistries
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.NbtOps
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientCommonPacketListener
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.tags.TagNetworkSerialization
import net.minecraft.world.level.storage.loot.LootDataType
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.debugger.helper.ReservedBreakpointIdStart
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.ArgumentTypeAdditionalDataSerializer
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.helper.SizeLimitedCallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.mixin.editor.processing.RecipeManagerAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.RegistrySynchronizationAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.TagPacketSerializerSerializedAccessor
import net.papierkorb2292.command_crafter.mixin.parser.CommandsAccessor
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

    fun getAllDynamicRegistries(): List<RegistryDataLoader.RegistryData<*>> = DynamicRegistries.getDynamicRegistries() + LootDataType.values().map {
        createRegistryLoaderEntryForLootDataType(it)
    }.toList()
    fun getSyncedRegistries() = getAllDynamicRegistries() + RegistryDataLoader.DIMENSION_REGISTRIES

    private val currentConnections = mutableMapOf<ServerGamePacketListenerImpl, DirectServerConnection>()

    private val scoreboardStorageWriteFileCombiner = PartialSequenceCombiner(PartialWriteFileParams::isLastPart, WriteFileParams::fromPartial)

    private val editorDebugConnections = mutableMapOf<ServerGamePacketListenerImpl, MutableMap<UUID, ServerNetworkDebugConnection>>()
    private val serverDebugPauses: MutableMap<UUID, ServerNetworkDebugConnection.DebugPauseInformation> = mutableMapOf()

    private val asyncServerPacketHandlers = mutableMapOf<CustomPacketPayload.Type<*>, AsyncPacketHandler<*, AsyncC2SPacketContext>>()
    private val asyncServerPacketHandlerExecutor = Executors.newSingleThreadExecutor()
    fun <TPayload : CustomPacketPayload> registerAsyncServerPacketHandler(id: CustomPacketPayload.Type<TPayload>, handler: AsyncPacketHandler<TPayload, AsyncC2SPacketContext>) {
        asyncServerPacketHandlers[id] = handler
    }
    fun <TPayload: CustomPacketPayload> callPacketHandler(packet: TPayload, context: AsyncC2SPacketContext): Boolean {
        val handler = asyncServerPacketHandlers[packet.type()] ?: return false
        asyncServerPacketHandlerExecutor.execute {
            @Suppress("UNCHECKED_CAST")
            (handler as AsyncPacketHandler<TPayload, AsyncC2SPacketContext>).receive(packet, context)
        }
        return true
    }

    fun isPlayerAllowedConnection(player: ServerPlayer) =
        Commands.LEVEL_GAMEMASTERS.check(player.permissions())

    fun registerPacketHandlers() {
        // Don't use registerAsyncServerPacketHandler here, because the client wouldn't be able to check whether a handler is registered for the packet
        ServerPlayNetworking.registerGlobalReceiver(RequestNetworkServerConnectionC2SPacket.ID) handler@{ payload, context ->
            if(!isPlayerAllowedConnection(context.player())) {
                context.responseSender().sendPacket(
                    InitializeNetworkServerConnectionS2CPacket(
                        false,
                        "insufficient permissions",
                        ClientboundCommandsPacket(RootCommandNode(), CommandsAccessor.getCOMMAND_NODE_INSPECTOR()),
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
                        ClientboundCommandsPacket(RootCommandNode(), CommandsAccessor.getCOMMAND_NODE_INSPECTOR()),
                        0,
                        payload.requestId
                    )
                )
                return@handler
            }

            val connection = DirectServerConnection(context.server())
            currentConnections[context.player().connection] = connection

            sendConnectionRequestResponse(
                context.server(),
                payload,
                connection,
                context.responseSender(),
                context.player().connection
            )

            context.responseSender().sendPacket(NotifyCanReloadWorldgenS2CPacket(connection.canReloadWorldgen))

            connection.serverLog?.addMessageCallback(
                object : SizeLimitedCallbackLinkedBlockingQueue.Callback<String> {
                    override fun onElementAdded(e: String) {
                        context.responseSender().sendPacket(LogMessageS2CPacket(e))
                    }

                    override fun shouldRemoveCallback() = context.player().hasDisconnected()
                }
            )
        }
        registerAsyncServerPacketHandler(SetBreakpointsRequestC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.connection] ?: return@registerAsyncServerPacketHandler
            val debugConnection = editorDebugConnections[context.player.connection]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
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
            val serverConnection = currentConnections[context.player.connection] ?: return@registerAsyncServerPacketHandler
            val debugConnection = editorDebugConnections[context.player.connection]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
            serverConnection.debugService.removeEditorDebugConnection(debugConnection)
        }
        registerAsyncServerPacketHandler(SourceReferenceRequestC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.connection] ?: return@registerAsyncServerPacketHandler
            val debugConnection = editorDebugConnections[context.player.connection]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
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
            val debugConnection = editorDebugConnections[context.player.connection]?.get(payload.debugConnectionId) ?: return@registerAsyncServerPacketHandler
            debugConnection.lifecycle.configurationDoneEvent.complete(null)
        }
        // This is async, so it isn't delayed until the next server tick, which sometimes caused "setBreakpoints" to be run first, which created its own DebugConnection
        registerAsyncServerPacketHandler(DebugConnectionRegistrationC2SPacket.ID) { payload, context->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            val serverConnection = currentConnections[context.player.connection] ?: return@registerAsyncServerPacketHandler
            val debugConnection = ServerNetworkDebugConnection(context.player, payload.debugConnectionId, payload.oneTimeDebugTarget, payload.nextSourceReference, payload.suspendServer)
            editorDebugConnections.getOrPut(context.player.connection, ::mutableMapOf).putIfAbsent(payload.debugConnectionId, debugConnection)
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
            val serverConnection = currentConnections[context.player.connection] ?: return@registerAsyncServerPacketHandler
            val server = context.server
            @Suppress("UNCHECKED_CAST")
            val reader = DirectiveStringReader(FileMappingInfo(payload.inputLines), server.commands.dispatcher as CommandDispatcher<SharedSuggestionProvider>, AnalyzingResourceCreator(null, ""))
            reader.cursor = payload.cursor
            serverConnection.contextCompletionProvider.getCompletions(reader).thenAccept {
                context.sendPacket(ContextCompletionResponseS2CPacket(payload.requestId, it))
            }
        }
        registerAsyncServerPacketHandler(ReloadDatapacksC2SPacket.ID) { payload, context ->
            if(!isPlayerAllowedConnection(context.player)) return@registerAsyncServerPacketHandler
            context.sendPacket(ReloadDatapacksAcknowledgementS2CPacket)
            val serverConnection = currentConnections[context.player.connection] ?: return@registerAsyncServerPacketHandler
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

    private fun <T: Any> createRegistryLoaderEntryForLootDataType(dataType: LootDataType<T>) =
        RegistryDataLoader.RegistryData(dataType.registryKey, dataType.codec, false)

    private fun sendConnectionRequestResponse(
        server: MinecraftServer,
        requestPacket: RequestNetworkServerConnectionC2SPacket,
        connection: DirectServerConnection,
        packetSender: PacketSender,
        networkHandler: ServerGamePacketListenerImpl,
    ) {
        sendDynamicRegistries(server, networkHandler)
        val functionPermissionLevel = (server.functionCompilationPermissions as? LevelBasedPermissionSet)?.level()?.id()
        if(functionPermissionLevel == null)
            CommandCrafter.LOGGER.warn("Unable to get function permission level for connection request: unexpected predicate type")
        @Suppress("UNCHECKED_CAST")
        val responsePacket = InitializeNetworkServerConnectionS2CPacket(
            true,
            null,
            ClientboundCommandsPacket(connection.commandDispatcher.root as RootCommandNode<CommandSourceStack>, CommandsAccessor.getCOMMAND_NODE_INSPECTOR()),
            functionPermissionLevel ?: 2,
            requestPacket.requestId
        )

        ArgumentTypeAdditionalDataSerializer.shouldWriteAdditionalDataTypes.runWithValue(true) {
            packetSender.sendPacket(responsePacket)
        }
    }

    fun sendDynamicRegistries(
        server: MinecraftServer,
        networkHandler: ServerGamePacketListenerImpl,
    ) {
        // Only send to players that have CommandCrafter installed and are connected
        if(networkHandler !in currentConnections)
            return

        val syncedRegistries = getSyncedRegistries()
        val syncedRegistryIds = syncedRegistries.mapTo(mutableSetOf()) { it.key.identifier() }

        // Used by the client to clear previous sync, just in case something went wrong
        networkHandler.send(ClientboundCustomPayloadPacket(StartRegistrySyncS2CPacket(syncedRegistryIds.toList())))

        // Can be cast to this type, because that is the value assigned in the DataPackContents constructor
        val registryManager = server.reloadableRegistries().lookup() as RegistryAccess

        val tagWrapperLookup = (server.recipeManager as RecipeManagerAccessor).registries

        val serializedRegistriesTags = serializeTags(tagWrapperLookup, registryManager)
        // Sync tags of non-dynamic registries first, because
        // client builds registry manager once all SYNCED_REGISTRIES have been received
        registryManager.listRegistryKeys().forEach {
            if(it.identifier() in syncedRegistryIds) return@forEach
            val registryTags = serializedRegistriesTags[it] ?: return@forEach
            networkHandler.send(
                ClientboundCustomPayloadPacket(
                    CommandCrafterDynamicRegistryS2CPacket(ClientboundRegistryDataPacket(it, emptyList()), registryTags)
                )
            )
        }
        syncedRegistries.forEach {
            sendDynamicRegistry(registryManager, it, networkHandler, serializedRegistriesTags[it.key])
        }
    }

    private fun serializeTags(
        tagWrapperLookup: HolderLookup.Provider,
        entryLookup: RegistryAccess,
    ): Map<ResourceKey<out Registry<*>>, TagNetworkSerialization.NetworkPayload> {
        return tagWrapperLookup.listRegistryKeys().map {
            val serializedTags = mutableMapOf<Identifier, IntList>()
            val tagRegistry = tagWrapperLookup.lookupOrThrow(it)
            val entryRegistry = entryLookup.lookupOrThrow(it)
            for(tag in tagRegistry.listTags().toList()) {
                val serialized = IntArrayList(tag.size())
                for(entry in tag) {
                    val id = entry.unwrapKey().orElseThrow { IllegalArgumentException("Synced tag entries must have an id") }
                    serialized.add(entryRegistry.getId(entryRegistry.getValue(id)))
                }
                serializedTags[tag.key().location] = serialized
            }
            it to TagPacketSerializerSerializedAccessor.callInit(serializedTags)
        }.collect(Collectors.toMap({ it.first }, { it.second }))
    }

    private fun sendDynamicRegistry(
        registryManager: RegistryAccess,
        registry: RegistryDataLoader.RegistryData<*>,
        networkHandler: ServerGamePacketListenerImpl,
        registryTags: TagNetworkSerialization.NetworkPayload?,
    ) {
        RegistrySynchronizationAccessor.callPackRegistry(
            registryManager.createSerializationContext(NbtOps.INSTANCE),
            registry,
            registryManager,
            emptySet()
        ) { registryKey, entries ->
            networkHandler.send(
                ClientboundCustomPayloadPacket(
                    CommandCrafterDynamicRegistryS2CPacket(ClientboundRegistryDataPacket(registryKey, entries), registryTags)
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

    private fun getServerScoreboardStorageFileSystem(player: ServerPlayer, id: UUID): ServerScoreboardStorageFileSystem? {
        val serverConnection = currentConnections[player.connection] ?: return null
        return ServerScoreboardStorageFileSystem.createdFileSystems.getOrPut(player.connection, ::mutableMapOf).getOrPut(id) {
            val fileSystem = serverConnection.createScoreboardStorageFileSystem()
            fileSystem.setOnDidChangeFileCallback {
                player.connection.send(
                    ClientboundCustomPayloadPacket(ScoreboardStorageFileNotificationS2CPacket.DID_CHANGE_FILE_PACKET.factory(id, it))
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
        val player: ServerPlayer,
        val server: MinecraftServer,
        val clientConnection: Connection,
    ) {
        fun sendPacket(packet: CustomPacketPayload) {
            sendPacket(ClientboundCustomPayloadPacket(packet))
        }

        fun sendPacket(packet: Packet<ClientCommonPacketListener>) {
            clientConnection.send(packet)
        }
    }
}