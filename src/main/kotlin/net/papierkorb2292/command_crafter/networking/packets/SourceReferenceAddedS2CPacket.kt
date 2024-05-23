package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class SourceReferenceAddedS2CPacket(val editorDebugConnection: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SourceReferenceAddedS2CPacket>(Identifier("command_crafter", "source_reference_added"))
        val CODEC: PacketCodec<ByteBuf, SourceReferenceAddedS2CPacket> = Uuids.PACKET_CODEC.xmap(
            ::SourceReferenceAddedS2CPacket,
            SourceReferenceAddedS2CPacket::editorDebugConnection
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, SourceReferenceAddedS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}