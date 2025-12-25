package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import java.util.*

class EditorDebugConnectionRemovedC2SPacket(val debugConnectionId: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<EditorDebugConnectionRemovedC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "editor_debug_connection_removed"))
        val CODEC: StreamCodec<ByteBuf, EditorDebugConnectionRemovedC2SPacket> = UUIDUtil.STREAM_CODEC.map(
            ::EditorDebugConnectionRemovedC2SPacket,
            EditorDebugConnectionRemovedC2SPacket::debugConnectionId
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, EditorDebugConnectionRemovedC2SPacket> = PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}