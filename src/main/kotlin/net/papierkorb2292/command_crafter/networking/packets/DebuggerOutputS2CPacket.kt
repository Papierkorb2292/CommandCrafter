package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.OUTPUT_EVENT_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.OutputEventArguments
import java.util.*

class DebuggerOutputS2CPacket(val args: OutputEventArguments, val editorDebugConnection: UUID) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DebuggerOutputS2CPacket>(Identifier("command_crafter", "debugger_output"))
        val CODEC: PacketCodec<ByteBuf, DebuggerOutputS2CPacket> = PacketCodec.tuple(
            OUTPUT_EVENT_ARGUMENTS_PACKET_CODEC,
            DebuggerOutputS2CPacket::args,
            Uuids.PACKET_CODEC,
            DebuggerOutputS2CPacket::editorDebugConnection,
            ::DebuggerOutputS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, DebuggerOutputS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }
    override fun getId() = ID
}