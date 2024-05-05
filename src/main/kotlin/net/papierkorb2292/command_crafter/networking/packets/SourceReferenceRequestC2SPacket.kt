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

class SourceReferenceRequestC2SPacket(val sourceReference: Int, val requestId: UUID, val debugConnectionId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SourceReferenceRequestC2SPacket>(Identifier("command_crafter", "source_reference_request"))
        val CODEC: PacketCodec<ByteBuf, SourceReferenceRequestC2SPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            SourceReferenceRequestC2SPacket::sourceReference,
            Uuids.PACKET_CODEC,
            SourceReferenceRequestC2SPacket::requestId,
            Uuids.PACKET_CODEC,
            SourceReferenceRequestC2SPacket::debugConnectionId,
            ::SourceReferenceRequestC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, SourceReferenceRequestC2SPacket> = PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}