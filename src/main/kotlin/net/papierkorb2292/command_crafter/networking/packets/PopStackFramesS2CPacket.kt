package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import java.util.*

class PopStackFramesS2CPacket(val amount: Int, val editorDebugConnection: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PopStackFramesS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debugger_pop_stack_frames"))
        val CODEC: StreamCodec<ByteBuf, PopStackFramesS2CPacket> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PopStackFramesS2CPacket::amount,
            UUIDUtil.STREAM_CODEC,
            PopStackFramesS2CPacket::editorDebugConnection,
            ::PopStackFramesS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, PopStackFramesS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}