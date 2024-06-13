package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class ReserveBreakpointIdsRequestS2CPacket(val count: Int, val editorDebugConnection: UUID, val requestId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ReserveBreakpointIdsRequestS2CPacket>(Identifier.of("command_crafter", "reserve_breakpoint_ids_request"))
        val CODEC: PacketCodec<ByteBuf, ReserveBreakpointIdsRequestS2CPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            ReserveBreakpointIdsRequestS2CPacket::count,
            Uuids.PACKET_CODEC,
            ReserveBreakpointIdsRequestS2CPacket::editorDebugConnection,
            Uuids.PACKET_CODEC,
            ReserveBreakpointIdsRequestS2CPacket::requestId,
            ::ReserveBreakpointIdsRequestS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, ReserveBreakpointIdsRequestS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}