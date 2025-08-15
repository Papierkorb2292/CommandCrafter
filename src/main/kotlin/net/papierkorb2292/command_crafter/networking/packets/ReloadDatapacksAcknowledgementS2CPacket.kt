package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

object ReloadDatapacksAcknowledgementS2CPacket : CustomPayload {
    val ID = CustomPayload.Id<ReloadDatapacksAcknowledgementS2CPacket>(Identifier.of("command_crafter", "reload_datapacks_acknowledgement"))
    val CODEC: PacketCodec<ByteBuf, ReloadDatapacksAcknowledgementS2CPacket> = PacketCodec.unit(ReloadDatapacksAcknowledgementS2CPacket)
    val TYPE: CustomPayload.Type<in RegistryByteBuf, ReloadDatapacksAcknowledgementS2CPacket> = PayloadTypeRegistry.playS2C().register(ID, CODEC)

    override fun getId() = ID
}