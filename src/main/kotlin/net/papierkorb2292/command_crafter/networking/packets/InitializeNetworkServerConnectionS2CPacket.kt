package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class InitializeNetworkServerConnectionS2CPacket(
    val successful: Boolean,
    val commandTree: CommandTreeS2CPacket,
    val functionPermissionLevel: Int,
    val requestId: UUID,
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<InitializeNetworkServerConnectionS2CPacket>(
            Identifier.of("command_crafter", "initialize_network_server_connection")
        )
        val CODEC: PacketCodec<PacketByteBuf, InitializeNetworkServerConnectionS2CPacket> = PacketCodec.tuple(
            PacketCodecs.BOOL,
            InitializeNetworkServerConnectionS2CPacket::successful,
            CommandTreeS2CPacket.CODEC,
            InitializeNetworkServerConnectionS2CPacket::commandTree,
            PacketCodecs.VAR_INT,
            InitializeNetworkServerConnectionS2CPacket::functionPermissionLevel,
            Uuids.PACKET_CODEC,
            InitializeNetworkServerConnectionS2CPacket::requestId,
            ::InitializeNetworkServerConnectionS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, InitializeNetworkServerConnectionS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}