package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.SOURCE_RESPONSE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.nullable
import org.eclipse.lsp4j.debug.SourceResponse
import java.util.*

class SourceReferenceResponseS2CPacket(val source: SourceResponse?, val requestId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SourceReferenceResponseS2CPacket>(Identifier("command_crafter", "source_reference_response"))
        val CODEC: PacketCodec<ByteBuf, SourceReferenceResponseS2CPacket> = PacketCodec.tuple(
            SOURCE_RESPONSE_PACKET_CODEC.nullable(),
            SourceReferenceResponseS2CPacket::source,
            Uuids.PACKET_CODEC,
            SourceReferenceResponseS2CPacket::requestId,
            ::SourceReferenceResponseS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, SourceReferenceResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}