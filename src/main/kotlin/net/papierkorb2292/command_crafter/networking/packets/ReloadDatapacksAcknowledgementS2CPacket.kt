package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

object ReloadDatapacksAcknowledgementS2CPacket : CustomPacketPayload {
    val ID = CustomPacketPayload.Type<ReloadDatapacksAcknowledgementS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "reload_datapacks_acknowledgement"))
    val CODEC: StreamCodec<ByteBuf, ReloadDatapacksAcknowledgementS2CPacket> = StreamCodec.unit(ReloadDatapacksAcknowledgementS2CPacket)
    val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ReloadDatapacksAcknowledgementS2CPacket> = PayloadTypeRegistry.playS2C().register(ID, CODEC)

    override fun type() = ID
}