package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.STEP_IN_TARGETS_RESPONSE_PACKET_CODEC
import org.eclipse.lsp4j.debug.StepInTargetsResponse
import java.util.*

class StepInTargetsResponseS2CPacket(val requestId: UUID, val response: StepInTargetsResponse): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<StepInTargetsResponseS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "step_in_targets_response"))
        val CODEC: StreamCodec<ByteBuf, StepInTargetsResponseS2CPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            StepInTargetsResponseS2CPacket::requestId,
            STEP_IN_TARGETS_RESPONSE_PACKET_CODEC,
            StepInTargetsResponseS2CPacket::response,
            ::StepInTargetsResponseS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, StepInTargetsResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}