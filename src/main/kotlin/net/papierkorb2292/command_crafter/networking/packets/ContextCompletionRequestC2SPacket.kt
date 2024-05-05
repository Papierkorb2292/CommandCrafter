package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

class ContextCompletionRequestC2SPacket(val completionId: Int, val inputLines: List<String>, val cursor: Int): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ContextCompletionRequestC2SPacket>(Identifier("command_crafter", "context_completion_request"))
        val CODEC: PacketCodec<ByteBuf, ContextCompletionRequestC2SPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            ContextCompletionRequestC2SPacket::completionId,
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