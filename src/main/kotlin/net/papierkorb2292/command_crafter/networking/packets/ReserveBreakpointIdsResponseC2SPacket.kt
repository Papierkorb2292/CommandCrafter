package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.debugger.helper.ReservedBreakpointIdStart
import java.util.*

class ReserveBreakpointIdsResponseC2SPacket(val start: ReservedBreakpointIdStart, val requestId: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ReserveBreakpointIdsResponseC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "reserve_breakpoint_ids_response"))
        val CODEC: StreamCodec<ByteBuf, ReserveBreakpointIdsResponseC2SPacket> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ReserveBreakpointIdsResponseC2SPacket::start,
            UUIDUtil.STREAM_CODEC,
            ReserveBreakpointIdsResponseC2SPacket::requestId,
            ::ReserveBreakpointIdsResponseC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ReserveBreakpointIdsResponseC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID

}