package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

object StartRegistrySyncS2CPacket : CustomPacketPayload {
    val ID = CustomPacketPayload.Type<StartRegistrySyncS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "start_registry_sync"))
    val CODEC: StreamCodec<FriendlyByteBuf, StartRegistrySyncS2CPacket> = StreamCodec.unit(StartRegistrySyncS2CPacket)
    val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, StartRegistrySyncS2CPacket> =
        PayloadTypeRegistry.playS2C().register(ID, CODEC)

    override fun type() = ID
}