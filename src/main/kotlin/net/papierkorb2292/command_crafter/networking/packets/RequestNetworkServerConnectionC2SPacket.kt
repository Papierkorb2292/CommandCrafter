package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import java.util.*

class RequestNetworkServerConnectionC2SPacket(val requestId: UUID, val clientModVersion: String): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<RequestNetworkServerConnectionC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "request_network_server_connection"))
        val CODEC: StreamCodec<ByteBuf, RequestNetworkServerConnectionC2SPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RequestNetworkServerConnectionC2SPacket::requestId,
            ByteBufCodecs.STRING_UTF8,
            RequestNetworkServerConnectionC2SPacket::clientModVersion,
            ::RequestNetworkServerConnectionC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, RequestNetworkServerConnectionC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }
    override fun type() = ID
}