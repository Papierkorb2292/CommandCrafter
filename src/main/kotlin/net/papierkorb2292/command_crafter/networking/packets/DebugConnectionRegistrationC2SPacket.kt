package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection.Companion.DEBUG_TARGET_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.nullable
import java.util.*

class DebugConnectionRegistrationC2SPacket(val oneTimeDebugTarget: EditorDebugConnection.DebugTarget?, val nextSourceReference: Int, val suspendServer: Boolean, val debugConnectionId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DebugConnectionRegistrationC2SPacket>(Identifier.of("command_crafter", "editor_debug_connection_registration"))
        val CODEC: PacketCodec<ByteBuf, DebugConnectionRegistrationC2SPacket> = PacketCodec.tuple(
            DEBUG_TARGET_PACKET_CODEC.nullable(),
            DebugConnectionRegistrationC2SPacket::oneTimeDebugTarget,
            PacketCodecs.VAR_INT,
            DebugConnectionRegistrationC2SPacket::nextSourceReference,
            PacketCodecs.BOOLEAN,
            DebugConnectionRegistrationC2SPacket::suspendServer,
            Uuids.PACKET_CODEC,
            DebugConnectionRegistrationC2SPacket::debugConnectionId,
            ::DebugConnectionRegistrationC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, DebugConnectionRegistrationC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}