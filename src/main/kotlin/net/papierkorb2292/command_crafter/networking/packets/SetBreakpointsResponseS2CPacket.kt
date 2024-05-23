package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.BREAKPOINT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.array
import org.eclipse.lsp4j.debug.Breakpoint
import java.util.*

class SetBreakpointsResponseS2CPacket(val breakpoints: Array<Breakpoint>, val requestId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SetBreakpointsResponseS2CPacket>(Identifier("command_crafter", "set_breakpoints_response"))
        val CODEC: PacketCodec<PacketByteBuf, SetBreakpointsResponseS2CPacket> = PacketCodec.tuple(
            BREAKPOINT_PACKET_CODEC.array(),
            SetBreakpointsResponseS2CPacket::breakpoints,
            Uuids.PACKET_CODEC,
            SetBreakpointsResponseS2CPacket::requestId,
            ::SetBreakpointsResponseS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, SetBreakpointsResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}