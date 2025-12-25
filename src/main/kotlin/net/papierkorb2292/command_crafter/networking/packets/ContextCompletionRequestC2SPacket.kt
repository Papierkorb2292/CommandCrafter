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

class ContextCompletionRequestC2SPacket(val requestId: UUID, val inputLines: List<String>, val cursor: Int):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ContextCompletionRequestC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "context_completion_request"))
        val CODEC: StreamCodec<ByteBuf, ContextCompletionRequestC2SPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ContextCompletionRequestC2SPacket::requestId,
            ByteBufCodecs.collection(::ArrayList, ByteBufCodecs.STRING_UTF8),
            ContextCompletionRequestC2SPacket::inputLines,
            ByteBufCodecs.VAR_INT,
            ContextCompletionRequestC2SPacket::cursor,
            ::ContextCompletionRequestC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ContextCompletionRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}