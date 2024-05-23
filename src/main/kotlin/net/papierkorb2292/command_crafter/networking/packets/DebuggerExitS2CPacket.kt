package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.EXITED_EVENT_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.ExitedEventArguments
import java.util.*

class DebuggerExitS2CPacket(val args: ExitedEventArguments, val editorDebugConnection: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DebuggerExitS2CPacket>(Identifier("command_crafter", "debugger_exit"))
        val CODEC: PacketCodec<ByteBuf, DebuggerExitS2CPacket> = PacketCodec.tuple(
            EXITED_EVENT_ARGUMENTS_PACKET_CODEC,
            DebuggerExitS2CPacket::args,
            Uuids.PACKET_CODEC,
            DebuggerExitS2CPacket::editorDebugConnection,
            ::DebuggerExitS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, DebuggerExitS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}