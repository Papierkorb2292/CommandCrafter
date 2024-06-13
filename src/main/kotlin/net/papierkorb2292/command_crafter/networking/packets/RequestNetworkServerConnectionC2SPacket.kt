package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class RequestNetworkServerConnectionC2SPacket(val requestId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<RequestNetworkServerConnectionC2SPacket>(Identifier.of("command_crafter", "request_network_server_connection"))
        val CODEC: PacketCodec<ByteBuf, RequestNetworkServerConnectionC2SPacket> = Uuids.PACKET_CODEC.xmap(
            ::RequestNetworkServerConnectionC2SPacket,
            RequestNetworkServerConnectionC2SPacket::requestId
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, RequestNetworkServerConnectionC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }
    override fun getId() = ID
}