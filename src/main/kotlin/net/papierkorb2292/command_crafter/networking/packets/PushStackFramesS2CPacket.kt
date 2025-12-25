package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import java.util.*

class PushStackFramesS2CPacket(val stackFrames: List<MinecraftStackFrame>, val editorDebugConnection: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PushStackFramesS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debugger_push_stack_frames"))
        val CODEC: StreamCodec<ByteBuf, PushStackFramesS2CPacket> = StreamCodec.composite(
            ByteBufCodecs.collection(::ArrayList, MinecraftStackFrame.PACKET_CODEC),
            PushStackFramesS2CPacket::stackFrames,
            UUIDUtil.STREAM_CODEC,
            PushStackFramesS2CPacket::editorDebugConnection,
            ::PushStackFramesS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, PushStackFramesS2CPacket> = PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}