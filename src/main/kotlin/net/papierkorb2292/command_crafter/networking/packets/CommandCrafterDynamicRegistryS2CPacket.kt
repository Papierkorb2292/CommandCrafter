package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.core.RegistrySynchronization.PackedRegistryEntry
import net.minecraft.tags.TagNetworkSerialization
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional

class CommandCrafterDynamicRegistryS2CPacket(
    /**
     * The dynamic registry. Has no entries for static registries.
     */
    val dynamicRegistry: ClientboundRegistryDataPacket,
    val tags: TagNetworkSerialization.NetworkPayload?,
) : CustomPacketPayload {
    companion object {
        // Uses custom packet codecs for dynamic registry to remove size limit for nbt elements (some datapacks can exceed the limit)
        private val UNLIMITED_REGISTRY_ENTRY_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            PackedRegistryEntry::id,
            ByteBufCodecs.TRUSTED_TAG.apply(ByteBufCodecs::optional),
            PackedRegistryEntry::data, ::PackedRegistryEntry
        )

        private val UNLIMITED_DYNAMIC_REGISTRY_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC.map(
                { ResourceKey.createRegistryKey<Any>(it) },
                ResourceKey<*>::identifier
            ),
            ClientboundRegistryDataPacket::registry,
            UNLIMITED_REGISTRY_ENTRY_CODEC.apply(ByteBufCodecs.list()),
            ClientboundRegistryDataPacket::entries,
            ::ClientboundRegistryDataPacket
        )

        val ID = CustomPacketPayload.Type<CommandCrafterDynamicRegistryS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "dynamic_registry"))
        val CODEC: StreamCodec<FriendlyByteBuf, CommandCrafterDynamicRegistryS2CPacket> = StreamCodec.composite(
            UNLIMITED_DYNAMIC_REGISTRY_CODEC,
            CommandCrafterDynamicRegistryS2CPacket::dynamicRegistry,
            StreamCodec.ofMember(
                TagNetworkSerialization.NetworkPayload::write,
                TagNetworkSerialization.NetworkPayload::read
            ).optional(),
            CommandCrafterDynamicRegistryS2CPacket::tags.toOptional(),
        ) { dynamicRegistry, tags ->
            CommandCrafterDynamicRegistryS2CPacket(dynamicRegistry, tags.orElse(null))
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, CommandCrafterDynamicRegistryS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}