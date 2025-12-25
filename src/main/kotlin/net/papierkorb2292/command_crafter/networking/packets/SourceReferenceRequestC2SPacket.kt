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

class SourceReferenceRequestC2SPacket(val sourceReference: Int, val requestId: UUID, val debugConnectionId: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SourceReferenceRequestC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "source_reference_request"))
        val CODEC: StreamCodec<ByteBuf, SourceReferenceRequestC2SPacket> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SourceReferenceRequestC2SPacket::sourceReference,
            UUIDUtil.STREAM_CODEC,
            SourceReferenceRequestC2SPacket::requestId,
            UUIDUtil.STREAM_CODEC,
            SourceReferenceRequestC2SPacket::debugConnectionId,
            ::SourceReferenceRequestC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, SourceReferenceRequestC2SPacket> = PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}