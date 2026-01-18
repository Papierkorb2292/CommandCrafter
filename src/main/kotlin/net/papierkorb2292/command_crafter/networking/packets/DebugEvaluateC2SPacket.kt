package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.networking.EVALUATE_ARGUMENTS_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import org.eclipse.lsp4j.debug.EvaluateArguments
import java.util.*

class DebugEvaluateC2SPacket(
    val requestId: UUID,
    val pauseId: UUID?,
    val debugConnectionId: UUID?,
    val args: EvaluateArguments,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DebugEvaluateC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debug_evaluate"))
        val CODEC: StreamCodec<ByteBuf, DebugEvaluateC2SPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            DebugEvaluateC2SPacket::requestId,
            UUIDUtil.STREAM_CODEC.optional(),
            DebugEvaluateC2SPacket::pauseId.toOptional(),
            UUIDUtil.STREAM_CODEC.optional(),
            DebugEvaluateC2SPacket::debugConnectionId.toOptional(),
            EVALUATE_ARGUMENTS_PACKET_CODEC,
            DebugEvaluateC2SPacket::args,
        ) { requestId, pauseId, debugConnectionId, args ->
            DebugEvaluateC2SPacket(requestId, pauseId.orElse(null), debugConnectionId.orElse(null), args)
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, DebugEvaluateC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}