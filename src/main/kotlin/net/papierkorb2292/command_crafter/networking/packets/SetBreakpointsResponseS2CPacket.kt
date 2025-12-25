package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.BREAKPOINT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.array
import org.eclipse.lsp4j.debug.Breakpoint
import java.util.*

class SetBreakpointsResponseS2CPacket(val breakpoints: Array<Breakpoint>, val requestId: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SetBreakpointsResponseS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "set_breakpoints_response"))
        val CODEC: StreamCodec<FriendlyByteBuf, SetBreakpointsResponseS2CPacket> = StreamCodec.composite(
            BREAKPOINT_PACKET_CODEC.array(),
            SetBreakpointsResponseS2CPacket::breakpoints,
            UUIDUtil.STREAM_CODEC,
            SetBreakpointsResponseS2CPacket::requestId,
            ::SetBreakpointsResponseS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, SetBreakpointsResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}