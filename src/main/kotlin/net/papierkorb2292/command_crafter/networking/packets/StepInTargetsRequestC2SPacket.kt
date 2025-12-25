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

class StepInTargetsRequestC2SPacket(val frameId: Int, val pauseId: UUID, val requestId: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<StepInTargetsRequestC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "step_in_targets_request"))
        val CODEC: StreamCodec<ByteBuf, StepInTargetsRequestC2SPacket> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            StepInTargetsRequestC2SPacket::frameId,
            UUIDUtil.STREAM_CODEC,
            StepInTargetsRequestC2SPacket::pauseId,
            UUIDUtil.STREAM_CODEC,
            StepInTargetsRequestC2SPacket::requestId,
            ::StepInTargetsRequestC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, StepInTargetsRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}