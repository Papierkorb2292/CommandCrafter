package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.debugger.helper.ReservedBreakpointIdStart
import java.util.*

class ReserveBreakpointIdsResponseC2SPacket(val start: ReservedBreakpointIdStart, val requestId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ReserveBreakpointIdsResponseC2SPacket>(Identifier.of("command_crafter", "reserve_breakpoint_ids_response"))
        val CODEC: PacketCodec<ByteBuf, ReserveBreakpointIdsResponseC2SPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            ReserveBreakpointIdsResponseC2SPacket::start,
            Uuids.PACKET_CODEC,
            ReserveBreakpointIdsResponseC2SPacket::requestId,
            ::ReserveBreakpointIdsResponseC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, ReserveBreakpointIdsResponseC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID

}