package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.OUTPUT_EVENT_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.OutputEventArguments
import java.util.*

class DebuggerOutputS2CPacket(val args: OutputEventArguments, val editorDebugConnection: UUID) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DebuggerOutputS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debugger_output"))
        val CODEC: StreamCodec<ByteBuf, DebuggerOutputS2CPacket> = StreamCodec.composite(
            OUTPUT_EVENT_ARGUMENTS_PACKET_CODEC,
            DebuggerOutputS2CPacket::args,
            UUIDUtil.STREAM_CODEC,
            DebuggerOutputS2CPacket::editorDebugConnection,
            ::DebuggerOutputS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, DebuggerOutputS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }
    override fun type() = ID
}