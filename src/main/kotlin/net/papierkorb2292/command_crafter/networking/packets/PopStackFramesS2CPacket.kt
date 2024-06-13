package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class PopStackFramesS2CPacket(val amount: Int, val editorDebugConnection: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PopStackFramesS2CPacket>(Identifier.of("command_crafter", "debugger_pop_stack_frames"))
        val CODEC: PacketCodec<ByteBuf, PopStackFramesS2CPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            PopStackFramesS2CPacket::amount,
            Uuids.PACKET_CODEC,
            PopStackFramesS2CPacket::editorDebugConnection,
            ::PopStackFramesS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, PopStackFramesS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}