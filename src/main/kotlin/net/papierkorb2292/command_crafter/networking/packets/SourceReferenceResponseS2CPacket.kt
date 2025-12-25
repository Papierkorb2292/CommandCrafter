package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.SOURCE_RESPONSE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import org.eclipse.lsp4j.debug.SourceResponse
import java.util.*

class SourceReferenceResponseS2CPacket(val source: SourceResponse?, val requestId: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SourceReferenceResponseS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "source_reference_response"))
        val CODEC: StreamCodec<ByteBuf, SourceReferenceResponseS2CPacket> = StreamCodec.composite(
            SOURCE_RESPONSE_PACKET_CODEC.optional(),
            SourceReferenceResponseS2CPacket::source.toOptional(),
            UUIDUtil.STREAM_CODEC,
            SourceReferenceResponseS2CPacket::requestId,
        ) { source, requestId ->
            SourceReferenceResponseS2CPacket(
                source.orElse(null),
                requestId
            )
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, SourceReferenceResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}