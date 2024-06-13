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

class ContextCompletionRequestC2SPacket(val requestId: UUID, val inputLines: List<String>, val cursor: Int): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ContextCompletionRequestC2SPacket>(Identifier.of("command_crafter", "context_completion_request"))
        val CODEC: PacketCodec<ByteBuf, ContextCompletionRequestC2SPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            ContextCompletionRequestC2SPacket::requestId,
            PacketCodecs.collection(::ArrayList, PacketCodecs.STRING),
            ContextCompletionRequestC2SPacket::inputLines,
            PacketCodecs.VAR_INT,
            ContextCompletionRequestC2SPacket::cursor,
            ::ContextCompletionRequestC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, ContextCompletionRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}