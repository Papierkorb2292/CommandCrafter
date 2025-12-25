package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

object ReloadDatapacksC2SPacket : CustomPacketPayload {
    val ID = CustomPacketPayload.Type<ReloadDatapacksC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "reload_datapacks"))
    val CODEC: StreamCodec<ByteBuf, ReloadDatapacksC2SPacket> = StreamCodec.unit(ReloadDatapacksC2SPacket)
    val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ReloadDatapacksC2SPacket> = PayloadTypeRegistry.playC2S().register(ID, CODEC)

    override fun type() = ID
}