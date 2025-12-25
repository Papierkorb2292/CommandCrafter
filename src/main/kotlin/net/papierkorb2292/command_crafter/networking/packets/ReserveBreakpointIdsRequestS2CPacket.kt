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

class ReserveBreakpointIdsRequestS2CPacket(val count: Int, val editorDebugConnection: UUID, val requestId: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ReserveBreakpointIdsRequestS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "reserve_breakpoint_ids_request"))
        val CODEC: StreamCodec<ByteBuf, ReserveBreakpointIdsRequestS2CPacket> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ReserveBreakpointIdsRequestS2CPacket::count,
            UUIDUtil.STREAM_CODEC,
            ReserveBreakpointIdsRequestS2CPacket::editorDebugConnection,
            UUIDUtil.STREAM_CODEC,
            ReserveBreakpointIdsRequestS2CPacket::requestId,
            ::ReserveBreakpointIdsRequestS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ReserveBreakpointIdsRequestS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}