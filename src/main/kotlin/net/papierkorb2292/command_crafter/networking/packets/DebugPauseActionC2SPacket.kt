package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkDebugPauseActions
import net.papierkorb2292.command_crafter.networking.NULLABLE_VAR_INT_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import net.papierkorb2292.command_crafter.networking.nullable
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*

class DebugPauseActionC2SPacket(val action: NetworkDebugPauseActions.DebugPauseAction, val granularity: SteppingGranularity?, val additionalInfo: Int? = null, val pauseId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<DebugPauseActionC2SPacket>(Identifier("command_crafter", "debug_pause_action"))
        val CODEC: PacketCodec<ByteBuf, DebugPauseActionC2SPacket> = PacketCodec.tuple(
            enumConstantCodec(NetworkDebugPauseActions.DebugPauseAction::class.java),
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
}