package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class EditorDebugConnectionRemovedC2SPacket(val debugConnectionId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<EditorDebugConnectionRemovedC2SPacket>(Identifier("command_crafter", "editor_debug_connection_removed"))
        val CODEC: PacketCodec<ByteBuf, EditorDebugConnectionRemovedC2SPacket> = Uuids.PACKET_CODEC.xmap(
            ::EditorDebugConnectionRemovedC2SPacket,
            EditorDebugConnectionRemovedC2SPacket::debugConnectionId
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, EditorDebugConnectionRemovedC2SPacket> = PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}