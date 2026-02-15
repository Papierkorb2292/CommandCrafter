package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.EvaluationProvider
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import java.util.*

class DebugEvaluateResponseS2CPacket(
    val requestId: UUID,
    val result: EvaluationProvider.EvaluationResult?
): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DebugEvaluateResponseS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debug_evaluate_response"))
        val CODEC: StreamCodec<ByteBuf, DebugEvaluateResponseS2CPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            DebugEvaluateResponseS2CPacket::requestId,
            EvaluationProvider.EVALUATION_RESULT_PACKET_CODEC.optional(),
            DebugEvaluateResponseS2CPacket::result.toOptional(),
        ) { requestId, result ->
            DebugEvaluateResponseS2CPacket(requestId, result.orElse(null))
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, DebugEvaluateResponseS2CPacket> =
            PayloadTypeRegistry.clientboundPlay().register(ID, CODEC)
    }

    override fun type() = ID
}