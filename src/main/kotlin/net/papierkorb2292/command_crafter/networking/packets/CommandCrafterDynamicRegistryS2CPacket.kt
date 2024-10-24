package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket
import net.minecraft.registry.tag.TagPacketSerializer
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.networking.nullable

class CommandCrafterDynamicRegistryS2CPacket(
    /**
     * The dynamic registry. Has no entries for static registries.
     */
    val dynamicRegistry: DynamicRegistriesS2CPacket,
    val tags: TagPacketSerializer.Serialized?
) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<CommandCrafterDynamicRegistryS2CPacket>(Identifier.of("command_crafter", "dynamic_registry"))
        val CODEC: PacketCodec<PacketByteBuf, CommandCrafterDynamicRegistryS2CPacket> = PacketCodec.tuple(
            DynamicRegistriesS2CPacket.CODEC,
            CommandCrafterDynamicRegistryS2CPacket::dynamicRegistry,
            PacketCodec.of(
                TagPacketSerializer.Serialized::writeBuf,
                TagPacketSerializer.Serialized::fromBuf
            ).nullable(),
            CommandCrafterDynamicRegistryS2CPacket::tags,
            ::CommandCrafterDynamicRegistryS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, CommandCrafterDynamicRegistryS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}