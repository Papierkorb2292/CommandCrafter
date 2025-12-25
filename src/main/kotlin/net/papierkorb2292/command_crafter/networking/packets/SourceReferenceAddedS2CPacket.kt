package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import java.util.*

class SourceReferenceAddedS2CPacket(val editorDebugConnection: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SourceReferenceAddedS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "source_reference_added"))
        val CODEC: StreamCodec<ByteBuf, SourceReferenceAddedS2CPacket> = UUIDUtil.STREAM_CODEC.map(
            ::SourceReferenceAddedS2CPacket,
            SourceReferenceAddedS2CPacket::editorDebugConnection
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, SourceReferenceAddedS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}