package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.networking.SOURCE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.UNPARSED_BREAKPOINT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.array
import org.eclipse.lsp4j.debug.Source
import java.util.*

class SetBreakpointsRequestC2SPacket(val breakpoints: Array<UnparsedServerBreakpoint>, val fileType: PackContentFileType, val id: PackagedId, val source: Source, val requestId: UUID, val debugConnectionId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SetBreakpointsRequestC2SPacket>(Identifier.of("command_crafter", "set_breakpoints_request"))
        val CODEC: PacketCodec<ByteBuf, SetBreakpointsRequestC2SPacket> = PacketCodec.tuple(
            UNPARSED_BREAKPOINT_PACKET_CODEC.array(),
            SetBreakpointsRequestC2SPacket::breakpoints,
            PackContentFileType.PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::fileType,
            PackagedId.PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::id,
            SOURCE_PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::source,
            Uuids.PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::requestId,
            Uuids.PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::debugConnectionId,
            ::SetBreakpointsRequestC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, SetBreakpointsRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}