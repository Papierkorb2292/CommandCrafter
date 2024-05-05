package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class ConfigurationDoneC2SPacket(val debugConnectionId: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<ConfigurationDoneC2SPacket>(Identifier("command_crafter", "debugger_configuration_done"))
        val CODEC: PacketCodec<ByteBuf, ConfigurationDoneC2SPacket> = Uuids.PACKET_CODEC.xmap(
            ::ConfigurationDoneC2SPacket,
            ConfigurationDoneC2SPacket::debugConnectionId
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, ConfigurationDoneC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}