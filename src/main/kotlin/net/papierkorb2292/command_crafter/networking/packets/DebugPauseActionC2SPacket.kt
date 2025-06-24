package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.networking.NULLABLE_VAR_INT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import net.papierkorb2292.command_crafter.networking.nullable
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*

class DebugPauseActionC2SPacket(val action: DebugPauseActionType, val granularity: SteppingGranularity?, val additionalInfo: Int? = null, val pauseId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DebugPauseActionC2SPacket>(Identifier.of("command_crafter", "debug_pause_action"))
        val CODEC: PacketCodec<ByteBuf, DebugPauseActionC2SPacket> = PacketCodec.tuple(
            enumConstantCodec(DebugPauseActionType::class.java),
            DebugPauseActionC2SPacket::action,
            enumConstantCodec(SteppingGranularity::class.java).nullable(),
            DebugPauseActionC2SPacket::granularity,
            NULLABLE_VAR_INT_PACKET_CODEC,
            DebugPauseActionC2SPacket::additionalInfo,
            Uuids.PACKET_CODEC,
            DebugPauseActionC2SPacket::pauseId,
            ::DebugPauseActionC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, DebugPauseActionC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID

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