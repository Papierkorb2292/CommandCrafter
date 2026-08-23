package net.papierkorb2292.command_crafter.networking.packets

import com.mojang.datafixers.util.Either
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import java.util.*

class ContextCompletionRequestC2SPacket(val requestId: UUID, val completionInfo: Either<ContextCompletionProvider.FunctionCompletionInfo, ContextCompletionProvider.MacroCompletionInfo>):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ContextCompletionRequestC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "context_completion_request"))
        val CODEC: StreamCodec<ByteBuf, ContextCompletionRequestC2SPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ContextCompletionRequestC2SPacket::requestId,
            ContextCompletionProvider.COMPLETION_INFO_PACKET_CODEC,
            ContextCompletionRequestC2SPacket::completionInfo,
            ::ContextCompletionRequestC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ContextCompletionRequestC2SPacket> =
            PayloadTypeRegistry.serverboundPlay().register(ID, CODEC)
    }

    override fun type() = ID
}