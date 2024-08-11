package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket
import net.minecraft.util.Identifier

class CommandCrafterDynamicRegistryS2CPacket(val dynamicRegistry: DynamicRegistriesS2CPacket) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<CommandCrafterDynamicRegistryS2CPacket>(Identifier.of("command_crafter", "dynamic_registry"))
        val CODEC: PacketCodec<PacketByteBuf, CommandCrafterDynamicRegistryS2CPacket> = DynamicRegistriesS2CPacket.CODEC.xmap(
            ::CommandCrafterDynamicRegistryS2CPacket,
            CommandCrafterDynamicRegistryS2CPacket::dynamicRegistry
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, CommandCrafterDynamicRegistryS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}