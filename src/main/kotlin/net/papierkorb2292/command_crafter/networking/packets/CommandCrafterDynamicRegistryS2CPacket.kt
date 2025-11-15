package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.nbt.NbtElement
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.SerializableRegistries.SerializedRegistryEntry
import net.minecraft.registry.tag.TagPacketSerializer
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.networking.nullable
import java.util.*
import java.util.function.BiFunction

class CommandCrafterDynamicRegistryS2CPacket(
    /**
     * The dynamic registry. Has no entries for static registries.
     */
    val dynamicRegistry: DynamicRegistriesS2CPacket,
    val tags: TagPacketSerializer.Serialized?,
) : CustomPayload {
    companion object {
        // Uses custom packet codecs for dynamic registry to remove size limit for nbt elements (some datapacks can exceed the limit)
        private val UNLIMITED_REGISTRY_ENTRY_CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC,
            SerializedRegistryEntry::id,
            PacketCodecs.UNLIMITED_NBT_ELEMENT.collect(PacketCodecs::optional),
            SerializedRegistryEntry::data, ::SerializedRegistryEntry
        )

        private val UNLIMITED_DYNAMIC_REGISTRY_CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC.xmap(
                { RegistryKey.ofRegistry<Any>(it) },
                RegistryKey<*>::getValue
            ),
            DynamicRegistriesS2CPacket::registry,
            UNLIMITED_REGISTRY_ENTRY_CODEC.collect(PacketCodecs.toList()),
            DynamicRegistriesS2CPacket::entries,
            ::DynamicRegistriesS2CPacket
        )

        val ID = CustomPayload.Id<CommandCrafterDynamicRegistryS2CPacket>(Identifier.of("command_crafter", "dynamic_registry"))
        val CODEC: PacketCodec<PacketByteBuf, CommandCrafterDynamicRegistryS2CPacket> = PacketCodec.tuple(
            UNLIMITED_DYNAMIC_REGISTRY_CODEC,
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