package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.STEP_IN_TARGETS_RESPONSE_PACKET_CODEC
import org.eclipse.lsp4j.debug.StepInTargetsResponse
import java.util.*

class StepInTargetsResponseS2CPacket(val requestId: UUID, val response: StepInTargetsResponse): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StepInTargetsResponseS2CPacket>(Identifier.of("command_crafter", "step_in_targets_response"))
        val CODEC: PacketCodec<ByteBuf, StepInTargetsResponseS2CPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            StepInTargetsResponseS2CPacket::requestId,
            STEP_IN_TARGETS_RESPONSE_PACKET_CODEC,
            StepInTargetsResponseS2CPacket::response,
            ::StepInTargetsResponseS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, StepInTargetsResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}