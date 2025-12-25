package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.networking.OPTIONAL_VAR_INT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*

class DebugPauseActionC2SPacket(val action: DebugPauseActionType, val granularity: SteppingGranularity?, val additionalInfo: Int? = null, val pauseId: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DebugPauseActionC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debug_pause_action"))
        val CODEC: StreamCodec<ByteBuf, DebugPauseActionC2SPacket> = StreamCodec.composite(
            enumConstantCodec(DebugPauseActionType::class.java),
            DebugPauseActionC2SPacket::action,
            enumConstantCodec(SteppingGranularity::class.java).optional(),
            DebugPauseActionC2SPacket::granularity.toOptional(),
            OPTIONAL_VAR_INT_PACKET_CODEC,
            DebugPauseActionC2SPacket::additionalInfo.toOptional(),
            UUIDUtil.STREAM_CODEC,
            DebugPauseActionC2SPacket::pauseId,
        ) { action, granularity, additionalInfo, pauseId ->
            DebugPauseActionC2SPacket(
                action,
                granularity.orElse(null),
                additionalInfo.orElse(null),
                pauseId
            )
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, DebugPauseActionC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID

    enum class DebugPauseActionType {
        NEXT,
        STEP_IN,
        STEP_OUT,
        CONTINUE;

        fun apply(actions: DebugPauseActions, packet: DebugPauseActionC2SPacket) {
            when(this) {
                NEXT -> actions.next(packet.granularity ?: SteppingGranularity.STATEMENT)
                STEP_IN -> actions.stepIn(packet.granularity ?: SteppingGranularity.STATEMENT, packet.additionalInfo)
                STEP_OUT -> actions.stepOut(packet.granularity ?: SteppingGranularity.STATEMENT)
                CONTINUE -> actions.continue_()
            }
        }
    }
}