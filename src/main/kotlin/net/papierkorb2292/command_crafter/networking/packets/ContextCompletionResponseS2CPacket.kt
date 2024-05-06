package net.papierkorb2292.command_crafter.networking.packets

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestions
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer
import net.papierkorb2292.command_crafter.networking.COMPLETION_ITEM_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.list
import org.eclipse.lsp4j.CompletionItem
import java.util.*

class ContextCompletionResponseS2CPacket(val requestId: UUID, val completions: List<CompletionItem>) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ContextCompletionResponseS2CPacket>(Identifier("command_crafter", "completion_items"))
        val CODEC: PacketCodec<ByteBuf, ContextCompletionResponseS2CPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            ContextCompletionResponseS2CPacket::requestId,
            COMPLETION_ITEM_PACKET_CODEC.list(),
            ContextCompletionResponseS2CPacket::completions,
            ::ContextCompletionResponseS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, ContextCompletionResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID

    fun asSuggestions(): Suggestions = Suggestions(StringRange.at(0), emptyList()).apply {
        @Suppress("KotlinConstantConditions")
        (this as CompletionItemsContainer).`command_crafter$setCompletionItem`(completions)
    }
}