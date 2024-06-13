package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import java.util.*

class PushStackFramesS2CPacket(val stackFrames: List<MinecraftStackFrame>, val editorDebugConnection: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PushStackFramesS2CPacket>(Identifier.of("command_crafter", "debugger_push_stack_frames"))
        val CODEC: PacketCodec<ByteBuf, PushStackFramesS2CPacket> = PacketCodec.tuple(
            PacketCodecs.collection(::ArrayList, MinecraftStackFrame.PACKET_CODEC),
            PushStackFramesS2CPacket::stackFrames,
            Uuids.PACKET_CODEC,
            PushStackFramesS2CPacket::editorDebugConnection,
            ::PushStackFramesS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, PushStackFramesS2CPacket> = PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}