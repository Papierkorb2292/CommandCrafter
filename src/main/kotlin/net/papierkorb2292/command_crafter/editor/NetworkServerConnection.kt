package net.papierkorb2292.command_crafter.editor

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Maps
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
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
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.console.PreLaunchLogListener
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkDebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkVariablesReferencer
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.helper.CallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.mixin.editor.ClientConnectionAccessor
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkServerConnection private constructor(private val client: MinecraftClient, initializePacket: InitializeNetworkServerConnectionS2CPacket) : MinecraftServerConnection {
    companion object {
        const val SERVER_LOG_CHANNEL = "server"
        private val requestConnectionPacketChannel = Identifier("command_crafter", "request_network_server_connection")
        private val initializeConnectionPacketChannel = Identifier("command_crafter", "initialize_network_server_connection")

        private val logMessagePacketChannel = Identifier("command_crafter", "log_message")
        val setBreakpointsRequestPacketChannel = Identifier("command_crafter", "set_breakpoints_request")

        val setBreakpointsResponsePacketChannel = Identifier("command_crafter", "set_breakpoints_response")

        val popStackFramesPacketChannel = Identifier("command_crafter", "debugger_pop_stack_frames")
        val pushStackFramesPacketChannel = Identifier("command_crafter", "debugger_push_stack_frames")
        val editorDebugConnectionRemovedPacketChannel = Identifier("command_crafter", "editor_debug_connection_removed")
        val setDebuggerPausedPacketChannel = Identifier("command_crafter", "set_debugger_paused")
        val updateReloadedBreakpointPacketChannel = Identifier("command_crafter", "update_reloaded_breakpoint")
        val debugPauseActionPacketChannel = Identifier("command_crafter", "debug_pause_action")
        val getVariablesRequestPacketChannel = Identifier("command_crafter", "get_variables_request")
        val getVariablesResponsePacketChannel = Identifier("command_crafter", "get_variables_response")
        val setVariableRequestPacketChannel = Identifier("command_crafter", "set_variable_request")
        val setVariableResponsePacketChannel = Identifier("command_crafter", "set_variable_response")
        val stepInTargetsRequestPacketChannel = Identifier("command_crafter", "step_in_targets_request")
        val stepInTargetsResponsePacketChannel = Identifier("command_crafter", "step_in_targets_response")
        val debuggerOutputPacketChannel = Identifier("command_crafter", "debugger_output")
        val sourceReferenceRequestPacketChannel = Identifier("command_crafter", "source_reference_request")
        val sourceReferenceResponsePacketChannel = Identifier("command_crafter", "source_reference_response")
        val reserveBreakpointIdsRequestPacketChannel = Identifier("command_crafter", "reserve_breakpoint_ids_request")
        val reserveBreakpointIdsResponsePacketChannel = Identifier("command_crafter", "reserve_breakpoint_ids_response")
        val debuggerConfigurationDonePacketChannel = Identifier("command_crafter", "debugger_configuration_done")
        val debuggerExitPacketChannel = Identifier("command_crafter", "debugger_exit")
        val editorDebugConnectionRegistrationPacketChannel = Identifier("command_crafter", "editor_debug_connection_registration")
        val sourceReferenceAddedPacketChannel: Identifier = Identifier("command_crafter", "source_reference_added")


        val currentGetVariablesRequests: MutableMap<UUID, CompletableFuture<Array<Variable>>> = mutableMapOf()
        val currentSetVariableRequests: MutableMap<UUID, CompletableFuture<VariablesReferencer.SetVariableResult?>> = mutableMapOf()
        val currentStepInTargetsRequests: MutableMap<UUID, CompletableFuture<StepInTargetsResponse>> = mutableMapOf()
        val currentSourceReferenceRequests: MutableMap<UUID, CompletableFuture<SourceResponse?>> = mutableMapOf()
        val currentBreakpointIdsRequests: MutableMap<UUID, CompletableFuture<ReservedBreakpointIdStart>> = mutableMapOf()

        private var currentConnectionRequest: Pair<UUID, CompletableFuture<NetworkServerConnection>>? = null
        private val currentBreakpointRequests: MutableMap<UUID, (Array<Breakpoint>) -> Unit> = Maps.newHashMap()

        private val clientEditorDebugConnections: BiMap<EditorDebugConnection, UUID> = HashBiMap.create()
        private val serverEditorDebugConnections: MutableMap<UUID, ServerNetworkDebugConnection> = mutableMapOf()
        private val serverDebugPauses: MutableMap<UUID, ServerNetworkDebugConnection.DebugPauseInformation> = mutableMapOf()

        fun requestAndCreate(): CompletableFuture<NetworkServerConnection> {
            if(!ClientPlayNetworking.canSend(requestConnectionPacketChannel)) {
                return CompletableFuture.failedFuture(ServerConnectionNotSupportedException("Server doesn't support editor connections"))
            }
            val requestId = UUID.randomUUID()
            val result = CompletableFuture<NetworkServerConnection>()
            currentConnectionRequest = requestId to result
            val packet = RequestNetworkServerConnectionC2SPacket(requestId)
            ClientPlayNetworking.send(requestConnectionPacketChannel, packet.write())
            return result
        }

        fun registerClientPacketHandlers() {
            ClientPlayNetworking.registerGlobalReceiver(initializeConnectionPacketChannel) handler@{ client, _, buf, _ ->

                val currentRequest = currentConnectionRequest
                    ?: return@handler
                val packet = InitializeNetworkServerConnectionS2CPacket(buf)
                if(packet.requestId != currentRequest.first) return@handler

                currentRequest.second.complete(NetworkServerConnection(client, packet))
            }
            ClientPlayNetworking.registerGlobalReceiver(logMessagePacketChannel) handler@{ _, _, buf, _ ->
                val packet = LogMessageS2CPacket(buf)
                val serverConnection = ClientCommandCrafter.editorConnectionManager.minecraftServerConnection
                if(serverConnection is NetworkServerConnection) {
                    serverConnection.serverLog?.run { log.add(packet.logMessage) }
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(setBreakpointsResponsePacketChannel) { _, _, buf, _ ->
                val packet = SetBreakpointsResponseS2CPacket(buf)
                currentBreakpointRequests.remove(packet.requestId)?.invoke(packet.breakpoints)
            }
            ClientPlayNetworking.registerGlobalReceiver(popStackFramesPacketChannel) { _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.PopStackFramesS2CPacket(buf)
                val editorConnection = clientEditorDebugConnections.inverse()[packet.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.popStackFrames(packet.amount)
            }
            ClientPlayNetworking.registerGlobalReceiver(pushStackFramesPacketChannel) { _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.PushStackFramesS2CPacket(buf)
                val editorConnection = clientEditorDebugConnections.inverse()[packet.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.pushStackFrames(packet.stackFrames)
            }
            ClientPlayNetworking.registerGlobalReceiver(setDebuggerPausedPacketChannel) handler@{ _, _, buf, packetSender ->
                val packet = ServerNetworkDebugConnection.PausedUpdateS2CPacket(buf)
                val editorConnection = clientEditorDebugConnections.inverse()[packet.editorDebugConnection] ?: return@handler
                val pause = packet.pause
                if(pause == null) editorConnection.pauseEnded()
                else editorConnection.pauseStarted(
                    NetworkDebugPauseActions(packetSender, pause.first),
                    pause.second,
                    NetworkVariablesReferencer(packetSender, pause.first)
                )
            }
            ClientPlayNetworking.registerGlobalReceiver(updateReloadedBreakpointPacketChannel) { _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.UpdateReloadedBreakpointS2CPacket(buf)
                clientEditorDebugConnections.inverse()[packet.editorDebugConnection]?.updateReloadedBreakpoint(packet.update)
            }
            ClientPlayNetworking.registerGlobalReceiver(getVariablesResponsePacketChannel) { _, _, buf, _ ->
                val packet = NetworkVariablesReferencer.GetVariablesResponseS2CPacket(buf)
                currentGetVariablesRequests.remove(packet.requestId)?.complete(packet.variables)
            }
            ClientPlayNetworking.registerGlobalReceiver(setVariableResponsePacketChannel) { _, _, buf, _ ->
                val packet = NetworkVariablesReferencer.SetVariableResponseS2CPacket(buf)
                currentSetVariableRequests.remove(packet.requestId)?.complete(packet.response)
            }
            ClientPlayNetworking.registerGlobalReceiver(stepInTargetsResponsePacketChannel) { _, _, buf, _ ->
                val packet = NetworkDebugPauseActions.StepInTargetsResponseS2CPacket(buf)
                currentStepInTargetsRequests.remove(packet.requestId)?.complete(packet.response)
            }
            ClientPlayNetworking.registerGlobalReceiver(debuggerOutputPacketChannel) { _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.DebuggerOutputS2CPacket(buf)
                clientEditorDebugConnections.inverse()[packet.editorDebugConnection]?.output(packet.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(sourceReferenceResponsePacketChannel) { _, _, buf, _ ->
                val packet = SourceReferenceResponseS2CPacket(buf)
                currentSourceReferenceRequests.remove(packet.requestId)?.complete(packet.source)
            }
            ClientPlayNetworking.registerGlobalReceiver(reserveBreakpointIdsRequestPacketChannel) { _, _, buf, sender ->
                val packet = ServerNetworkDebugConnection.ReserveBreakpointIdsRequestS2CPacket(buf)
                clientEditorDebugConnections.inverse()[packet.editorDebugConnection]?.reserveBreakpointIds(packet.count)?.thenAccept {
                    sender.sendPacket(
                        reserveBreakpointIdsResponsePacketChannel,
                        ServerNetworkDebugConnection.ReserveBreakpointIdsResponseC2SPacket(it, packet.requestId).write()
                    )
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(debuggerExitPacketChannel) { _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.DebuggerExitS2CPacket(buf)
                val editorConnection = clientEditorDebugConnections.inverse()[packet.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.lifecycle.shouldExitEvent.complete(packet.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(sourceReferenceAddedPacketChannel) { _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.SourceReferenceAddedS2CPacket(buf)
                clientEditorDebugConnections.inverse()[packet.editorDebugConnection]?.onSourceReferenceAdded()
            }
            ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
                clientEditorDebugConnections.clear()
            }
        }

        fun registerServerPacketHandlers() {
            ServerPlayNetworking.registerGlobalReceiver(requestConnectionPacketChannel) handler@{ server, player, _, buf, packetSender ->
                //TODO: Whitelist for which players are allowed to connect this way
                val requestPacket = RequestNetworkServerConnectionC2SPacket(buf)

                sendConnectionRequestResponse(server, requestPacket, packetSender)

                if(server.isDedicated) {
                    startSendingLogMessages(packetSender, player)
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(setBreakpointsRequestPacketChannel) { server, player, _, buf, packetSender ->
                val requestPacket = SetBreakpointsRequestC2SPacket(buf)
                val debugManager = server.getDebugManager()
                server.execute {
                    packetSender.sendPacket(
                        setBreakpointsResponsePacketChannel,
                        SetBreakpointsResponseS2CPacket(debugManager.setBreakpoints(
                            requestPacket.breakpoints,
                            requestPacket.fileType,
                            requestPacket.id,
                            player,
                            serverEditorDebugConnections.computeIfAbsent(requestPacket.debugConnectionId) {
                                ServerNetworkDebugConnection(player, it)
                            },
                            requestPacket.sourceReference
                        ), requestPacket.requestId).write()
                    )
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(editorDebugConnectionRemovedPacketChannel) { server, _, _, buf, _ ->
                val packet = EditorDebugConnectionRemovedC2SPacket(buf)
                val debugConnection = serverEditorDebugConnections.remove(packet.debugConnectionId) ?: return@registerGlobalReceiver
                debugConnection.currentPauseId?.run {
                    serverDebugPauses.remove(this)?.actions?.continue_()
                }
                val debugManager = server.getDebugManager()
                server.execute { debugManager.removeDebugConnection(debugConnection) }
            }
            ServerPlayNetworking.registerGlobalReceiver(debugPauseActionPacketChannel) { server, _, _, buf, _ ->
                val packet = NetworkDebugPauseActions.DebugPauseActionC2SPacket(buf)
                val debugPause = serverDebugPauses[packet.pauseId] ?: return@registerGlobalReceiver
                packet.action.apply(debugPause.actions, packet)
            }
            ServerPlayNetworking.registerGlobalReceiver(getVariablesRequestPacketChannel) { server, _, _, buf, packetSender ->
                val packet = NetworkVariablesReferencer.GetVariablesRequestC2SPacket(buf)
                val debugPause = serverDebugPauses[packet.pauseId] ?: return@registerGlobalReceiver
                server.execute {
                    debugPause.pauseContext.getVariables(packet.args).thenAccept {
                        packetSender.sendPacket(
                            getVariablesResponsePacketChannel,
                            NetworkVariablesReferencer.GetVariablesResponseS2CPacket(packet.requestId, it).write()
                        )
                    }
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(setVariableRequestPacketChannel) { server, _, _, buf, packetSender ->
                val packet = NetworkVariablesReferencer.SetVariableRequestC2SPacket(buf)
                val debugPause = serverDebugPauses[packet.pauseId] ?: return@registerGlobalReceiver
                server.execute {
                    debugPause.pauseContext.setVariable(packet.args).thenAccept {
                        packetSender.sendPacket(
                            setVariableResponsePacketChannel,
                            NetworkVariablesReferencer.SetVariableResponseS2CPacket(packet.requestId, it).write()
                        )
                    }
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(stepInTargetsRequestPacketChannel) { server, _, _, buf, packetSender ->
                val packet = NetworkDebugPauseActions.StepInTargetsRequestC2SPacket(buf)
                val debugPause = serverDebugPauses[packet.pauseId] ?: return@registerGlobalReceiver
                server.execute {
                    debugPause.actions.stepInTargets(packet.frameId).thenAccept {
                        packetSender.sendPacket(
                            stepInTargetsResponsePacketChannel,
                            NetworkDebugPauseActions.StepInTargetsResponseS2CPacket(packet.requestId, it).write()
                        )
                    }
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(sourceReferenceRequestPacketChannel) { server, _, _, buf, packetSender ->
                val packet = SourceReferenceRequestC2SPacket(buf)
                val debugConnection = serverEditorDebugConnections[packet.debugConnectionId] ?: return@registerGlobalReceiver
                val debugManager = server.getDebugManager()
                server.execute {
                    val sourceResponse = debugManager.retrieveSourceReference(debugConnection, packet.sourceReference)
                    packetSender.sendPacket(
                        sourceReferenceResponsePacketChannel,
                        SourceReferenceResponseS2CPacket(sourceResponse, packet.requestId).write()
                    )
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(reserveBreakpointIdsResponsePacketChannel) { _, _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.ReserveBreakpointIdsResponseC2SPacket(buf)
                currentBreakpointIdsRequests.remove(packet.requestId)?.complete(packet.start)
            }
            ServerPlayNetworking.registerGlobalReceiver(debuggerConfigurationDonePacketChannel) { _, _, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.ConfigurationDoneC2SPacket(buf)
                val debugConnection = serverEditorDebugConnections[packet.debugConnectionId] ?: return@registerGlobalReceiver
                debugConnection.lifecycle.configurationDoneEvent.complete(null)
            }
            ServerPlayNetworking.registerGlobalReceiver(editorDebugConnectionRegistrationPacketChannel) { _, player, _, buf, _ ->
                val packet = ServerNetworkDebugConnection.DebugConnectionRegistrationC2SPacket(buf)
                serverEditorDebugConnections.putIfAbsent(packet.debugConnectionId, ServerNetworkDebugConnection(player, packet.debugConnectionId, packet.oneTimeDebugTarget, packet.nextSourceReference, packet.suspendServer))
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
            PreLaunchLogListener.addLogListener(object : CallbackLinkedBlockingQueue.Callback<String> {
                override fun onElementAdded(e: String) {
                    val packet = LogMessageS2CPacket(e)
                    packetSender.sendPacket(logMessagePacketChannel, packet.write())
                }

                override fun shouldRemoveCallback() = player.isDisconnected
            })
        }

        private fun sendConnectionRequestResponse(
            server: MinecraftServer,
            requestPacket: RequestNetworkServerConnectionC2SPacket,
            packetSender: PacketSender,
        ) {
            val map: MutableMap<CommandNode<ServerCommandSource>, CommandNode<CommandSource>> = Maps.newHashMap()
            val rootCommandNode = RootCommandNode<CommandSource>()
            val commandManager = server.commandManager
            val dispatcher = commandManager.dispatcher
            map[dispatcher.root] = rootCommandNode
            val serverCommandSource = ServerCommandSource(
                CommandOutput.DUMMY,
                Vec3d.ZERO,
                Vec2f.ZERO,
                null,
                server.functionPermissionLevel,
                "",
                ScreenTexts.EMPTY,
                null,
                null
            )
            (commandManager as CommandManagerAccessor).callMakeTreeForSource(
                dispatcher.root,
                rootCommandNode,
                serverCommandSource,
                map
            )

            val responsePacket = InitializeNetworkServerConnectionS2CPacket(
                CommandTreeS2CPacket(rootCommandNode),
                server.functionPermissionLevel,
                requestPacket.requestId,
            )

            try {
                IdArgumentTypeAnalyzer.shouldAddPackContentFileType.set(true)
                packetSender.sendPacket(initializeConnectionPacketChannel, responsePacket.write())
            } finally {
                IdArgumentTypeAnalyzer.shouldAddPackContentFileType.remove()
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

    @Suppress("UNCHECKED_CAST")
    override val commandDispatcher: CommandDispatcher<ServerCommandSource> = CommandDispatcher(initializePacket.commandTree.getCommandTree(CommandRegistryAccess.of(client.networkHandler?.registryManager, client.networkHandler?.enabledFeatures))) as CommandDispatcher<ServerCommandSource>
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
            id: Identifier,
            editorDebugConnection: EditorDebugConnection
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
                setBreakpointsRequestPacketChannel,
                SetBreakpointsRequestC2SPacket(breakpoints, fileType, id, source.sourceReference, requestId, debugConnectionId).write()
            )
            return completableFuture
        }

        override fun retrieveSourceReference(sourceReference: Int, editorDebugConnection: EditorDebugConnection): CompletableFuture<SourceResponse?> {
            val debugConnectionId = getOrCreateDebugConnectionId(editorDebugConnection)
            val completableFuture = CompletableFuture<SourceResponse?>()
            val requestId = UUID.randomUUID()
            currentSourceReferenceRequests[requestId] = completableFuture
            ClientPlayNetworking.send(
                sourceReferenceRequestPacketChannel,
                SourceReferenceRequestC2SPacket(sourceReference, requestId, debugConnectionId).write()
            )
            return completableFuture
        }

        override fun removeEditorDebugConnection(editorDebugConnection: EditorDebugConnection) {
            val id = clientEditorDebugConnections.remove(editorDebugConnection) ?: return
            ClientPlayNetworking.send(
                editorDebugConnectionRemovedPacketChannel,
                EditorDebugConnectionRemovedC2SPacket(id).write()
            )
        }

        private fun getOrCreateDebugConnectionId(editorDebugConnection: EditorDebugConnection): UUID {
            return clientEditorDebugConnections.computeIfAbsent(editorDebugConnection) {
                val id = UUID.randomUUID()
                ClientPlayNetworking.send(
                    editorDebugConnectionRegistrationPacketChannel,
                    ServerNetworkDebugConnection.DebugConnectionRegistrationC2SPacket(it.oneTimeDebugTarget, it.nextSourceReference, it.suspendServer, id).write()
                )
                it.lifecycle.configurationDoneEvent.thenRun {
                    ClientPlayNetworking.send(
                        debuggerConfigurationDonePacketChannel,
                        ServerNetworkDebugConnection.ConfigurationDoneC2SPacket(id).write()
                    )
                }
                id
            }
        }
    }

    class NetworkServerLog : Log {
        val log = CallbackLinkedBlockingQueue<String>()
        override val name
            get() = SERVER_LOG_CHANNEL
        override fun addMessageCallback(callback: CallbackLinkedBlockingQueue.Callback<String>) {
            log.addCallback(callback)
        }
    }

    class NetworkCommandExecutor(private val client: MinecraftClient) : CommandExecutor {
        override fun executeCommand(command: String) {
            client.inGameHud.chatHud.addToMessageHistory("/$command") //TODO: Test how multiple lines work in chat screen
            client.networkHandler?.sendChatCommand(command)
        }
    }

    class RequestNetworkServerConnectionC2SPacket(val requestId: UUID) : ByteBufWritable {

        constructor(buf: PacketByteBuf) : this(buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(requestId)
        }
    }

    class InitializeNetworkServerConnectionS2CPacket(
        val commandTree: CommandTreeS2CPacket,
        val functionPermissionLevel: Int,
        val requestId: UUID,
    ) : ByteBufWritable {

        constructor(buf: PacketByteBuf) : this(CommandTreeS2CPacket(buf), buf.readVarInt(), buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            commandTree.write(buf)
            buf.writeVarInt(functionPermissionLevel)
            buf.writeUuid(requestId)
        }
    }

    class LogMessageS2CPacket(val logMessage: String): ByteBufWritable {

        constructor(buf: PacketByteBuf) : this(buf.readString())

        override fun write(buf: PacketByteBuf) {
            buf.writeString(logMessage)
        }
    }

    class SetBreakpointsRequestC2SPacket(val breakpoints: Array<UnparsedServerBreakpoint>, val fileType: PackContentFileType, val id: Identifier, val sourceReference: Int?, val requestId: UUID, val debugConnectionId: UUID): ByteBufWritable {

        constructor(buf: PacketByteBuf) : this(
            Array(buf.readVarInt()) {
                val id = buf.readVarInt()
                val sourceReference = buf.readNullableInt()
                val sourceBreakpoint = SourceBreakpoint()
                sourceBreakpoint.line = buf.readVarInt()
                sourceBreakpoint.column = buf.readNullableInt()
                sourceBreakpoint.condition = buf.readNullableString()
                sourceBreakpoint.hitCondition = buf.readNullableString()
                sourceBreakpoint.logMessage = buf.readNullableString()
                UnparsedServerBreakpoint(id, sourceReference, sourceBreakpoint)
            },
            buf.readEnumConstant(PackContentFileType::class.java),
            buf.readIdentifier(),
            buf.readNullableInt(),
            buf.readUuid(),
            buf.readUuid()
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeVarInt(breakpoints.size)
            for(breakpoint in breakpoints) {
                buf.writeVarInt(breakpoint.id)
                buf.writeNullableInt(breakpoint.sourceReference)
                val sourceBreakpoint = breakpoint.sourceBreakpoint
                buf.writeVarInt(sourceBreakpoint.line)
                buf.writeNullableInt(sourceBreakpoint.column)
                buf.writeNullableString(sourceBreakpoint.condition)
                buf.writeNullableString(sourceBreakpoint.hitCondition)
                buf.writeNullableString(sourceBreakpoint.logMessage)
            }
            buf.writeEnumConstant(fileType)
            buf.writeIdentifier(id)
            buf.writeNullableInt(sourceReference)
            buf.writeUuid(requestId)
            buf.writeUuid(debugConnectionId)
        }
    }

    class SetBreakpointsResponseS2CPacket(val breakpoints: Array<Breakpoint>, val requestId: UUID) : ByteBufWritable {
        constructor(buf: PacketByteBuf): this(
            Array(buf.readVarInt()) { buf.readBreakpoint() },
            buf.readUuid()
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeVarInt(breakpoints.size)
            for(breakpoint in breakpoints) {
                buf.writeBreakpoint(breakpoint)
            }
            buf.writeUuid(requestId)
        }
    }

    class EditorDebugConnectionRemovedC2SPacket(val debugConnectionId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(debugConnectionId)
        }
    }

    class SourceReferenceRequestC2SPacket(val sourceReference: Int, val requestId: UUID, val debugConnectionId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readVarInt(), buf.readUuid(), buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeVarInt(sourceReference)
            buf.writeUuid(requestId)
            buf.writeUuid(debugConnectionId)
        }
    }

    class SourceReferenceResponseS2CPacket(val source: SourceResponse?, val requestId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readNullable {
            SourceResponse().apply {
                content = it.readString()
                mimeType = it.readNullableString()
            }
        }, buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeNullable(source) { sourceBuf, source ->
                sourceBuf.writeString(source.content)
                sourceBuf.writeNullableString(source.mimeType)
            }
            buf.writeUuid(requestId)
        }

    }

    class ServerConnectionNotSupportedException(message: String?) : Exception(message) {
        constructor() : this(null)
    }
}
