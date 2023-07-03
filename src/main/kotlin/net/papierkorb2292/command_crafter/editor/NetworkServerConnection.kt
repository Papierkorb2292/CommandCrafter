package net.papierkorb2292.command_crafter.editor

import com.google.common.collect.Maps
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import io.netty.channel.local.LocalChannel
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
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
import net.papierkorb2292.command_crafter.helper.CallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.mixin.editor.ClientConnectionAccessor
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor
import net.papierkorb2292.command_crafter.networking.Packet
import net.papierkorb2292.command_crafter.networking.write
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkServerConnection private constructor(private val client: MinecraftClient, initializePacket: InitializeNetworkServerConnectionS2CPacket) : MinecraftServerConnection {
    companion object {
        const val SERVER_LOG_CHANNEL = "server"

        private val requestConnectionPacketChannel = Identifier("command_crafter", "request_network_server_connection")
        private val initializeConnectionPacketChannel = Identifier("command_crafter", "initialize_network_server_connection")
        private val logMessagePacketChannel = Identifier("command_crafter", "log_message")

        private var currentConnectionRequest: Pair<UUID, CompletableFuture<NetworkServerConnection>>? = null

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

            packetSender.sendPacket(initializeConnectionPacketChannel, responsePacket.write())
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

    class RequestNetworkServerConnectionC2SPacket(val requestId: UUID) : Packet {

        constructor(buf: PacketByteBuf) : this(buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(requestId)
        }
    }

    class InitializeNetworkServerConnectionS2CPacket(
        val commandTree: CommandTreeS2CPacket,
        val functionPermissionLevel: Int,
        val requestId: UUID,
    ) : Packet {

        constructor(buf: PacketByteBuf) : this(CommandTreeS2CPacket(buf), buf.readVarInt(), buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            commandTree.write(buf)
            buf.writeVarInt(functionPermissionLevel)
            buf.writeUuid(requestId)
        }
    }

    class LogMessageS2CPacket(val logMessage: String): Packet {

        constructor(buf: PacketByteBuf) : this(buf.readString())

        override fun write(buf: PacketByteBuf) {
            buf.writeString(logMessage)
        }
    }

    class ServerConnectionNotSupportedException(message: String?) : Exception(message) {
        constructor() : this(null)
    }
}
