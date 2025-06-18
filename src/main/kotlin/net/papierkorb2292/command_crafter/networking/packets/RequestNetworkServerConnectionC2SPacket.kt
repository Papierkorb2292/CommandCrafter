package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class RequestNetworkServerConnectionC2SPacket(val requestId: UUID, val clientModVersion: String): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<RequestNetworkServerConnectionC2SPacket>(Identifier.of("command_crafter", "request_network_server_connection"))
        val CODEC: PacketCodec<ByteBuf, RequestNetworkServerConnectionC2SPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            RequestNetworkServerConnectionC2SPacket::requestId,
            PacketCodecs.STRING,
            RequestNetworkServerConnectionC2SPacket::clientModVersion,
            ::RequestNetworkServerConnectionC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, RequestNetworkServerConnectionC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }
    override fun getId() = ID
}