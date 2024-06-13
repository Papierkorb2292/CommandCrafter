package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class StepInTargetsRequestC2SPacket(val frameId: Int, val pauseId: UUID, val requestId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StepInTargetsRequestC2SPacket>(Identifier.of("command_crafter", "step_in_targets_request"))
        val CODEC: PacketCodec<ByteBuf, StepInTargetsRequestC2SPacket> = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            StepInTargetsRequestC2SPacket::frameId,
            Uuids.PACKET_CODEC,
            StepInTargetsRequestC2SPacket::pauseId,
            Uuids.PACKET_CODEC,
            StepInTargetsRequestC2SPacket::requestId,
            ::StepInTargetsRequestC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, StepInTargetsRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}