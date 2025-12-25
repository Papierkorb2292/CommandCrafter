package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.COMPLETION_ITEM_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.list
import org.eclipse.lsp4j.CompletionItem
import java.util.*

class ContextCompletionResponseS2CPacket(val requestId: UUID, val completions: List<CompletionItem>) :
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ContextCompletionResponseS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "completion_items"))
        val CODEC: StreamCodec<ByteBuf, ContextCompletionResponseS2CPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ContextCompletionResponseS2CPacket::requestId,
            COMPLETION_ITEM_PACKET_CODEC.list(),
            ContextCompletionResponseS2CPacket::completions,
            ::ContextCompletionResponseS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ContextCompletionResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}