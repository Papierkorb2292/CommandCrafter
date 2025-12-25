package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.networking.SOURCE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.UNPARSED_BREAKPOINT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.array
import org.eclipse.lsp4j.debug.Source
import java.util.*

class SetBreakpointsRequestC2SPacket(val breakpoints: Array<UnparsedServerBreakpoint>, val fileType: PackContentFileType, val id: PackagedId, val source: Source, val requestId: UUID, val debugConnectionId: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SetBreakpointsRequestC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "set_breakpoints_request"))
        val CODEC: StreamCodec<ByteBuf, SetBreakpointsRequestC2SPacket> = StreamCodec.composite(
            UNPARSED_BREAKPOINT_PACKET_CODEC.array(),
            SetBreakpointsRequestC2SPacket::breakpoints,
            PackContentFileType.PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::fileType,
            PackagedId.PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::id,
            SOURCE_PACKET_CODEC,
            SetBreakpointsRequestC2SPacket::source,
            UUIDUtil.STREAM_CODEC,
            SetBreakpointsRequestC2SPacket::requestId,
            UUIDUtil.STREAM_CODEC,
            SetBreakpointsRequestC2SPacket::debugConnectionId,
            ::SetBreakpointsRequestC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, SetBreakpointsRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}