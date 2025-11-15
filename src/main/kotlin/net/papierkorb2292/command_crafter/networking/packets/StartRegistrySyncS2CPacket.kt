package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

object StartRegistrySyncS2CPacket : CustomPayload {
    val ID = CustomPayload.Id<StartRegistrySyncS2CPacket>(Identifier.of("command_crafter", "start_registry_sync"))
    val CODEC: PacketCodec<PacketByteBuf, StartRegistrySyncS2CPacket> = PacketCodec.unit(StartRegistrySyncS2CPacket)
    val TYPE: CustomPayload.Type<in RegistryByteBuf, StartRegistrySyncS2CPacket> =
        PayloadTypeRegistry.playS2C().register(ID, CODEC)

    override fun getId() = ID
}