package net.papierkorb2292.command_crafter.editor

import com.google.common.collect.Maps
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor
import net.papierkorb2292.command_crafter.networking.Packet
import net.papierkorb2292.command_crafter.networking.write
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkServerConnection private constructor(private val networkHandler: ClientPlayNetworkHandler, initializePacket: InitializeNetworkServerConnectionS2CPacket) : MinecraftServerConnection {
    companion object {
        private val requestConnectionPacketChannel = Identifier("command_crafter", "request_network_server_connection")
        private val initializeConnectionPacketChannel = Identifier("command_crafter", "initialize_network_server_connection")

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
            ClientPlayNetworking.registerGlobalReceiver(initializeConnectionPacketChannel) handler@{ _, networkHandler, buf, _ ->

                val currentRequest = currentConnectionRequest
                    ?: return@handler
                val packet = InitializeNetworkServerConnectionS2CPacket(buf)
                if(packet.requestId != currentRequest.first) return@handler

                currentRequest.second.complete(NetworkServerConnection(networkHandler, packet))
            }
        }

        fun registerServerPacketHandlers() {
            ServerPlayNetworking.registerGlobalReceiver(requestConnectionPacketChannel) handler@{ server, _, networkHandler, buf, packetSender ->

                val requestPacket = RequestNetworkServerConnectionC2SPacket(buf)

                val map: MutableMap<CommandNode<ServerCommandSource>, CommandNode<CommandSource>> = Maps.newHashMap()
                val rootCommandNode = RootCommandNode<CommandSource>()
                val commandManager = server.commandManager
                val dispatcher = commandManager.dispatcher
                map[dispatcher.root] = rootCommandNode
                val serverCommandSource = ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, server.functionPermissionLevel, "", ScreenTexts.EMPTY, null, null)
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
    }

    @Suppress("UNCHECKED_CAST")
    override val commandDispatcher: CommandDispatcher<ServerCommandSource> = CommandDispatcher(initializePacket.commandTree.getCommandTree(CommandRegistryAccess.of(networkHandler.registryManager, networkHandler.enabledFeatures))) as CommandDispatcher<ServerCommandSource>
    override val functionPermissionLevel = initializePacket.functionPermissionLevel

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

    class ServerConnectionNotSupportedException(message: String?) : Exception(message) {
        constructor() : this(null)
    }
}