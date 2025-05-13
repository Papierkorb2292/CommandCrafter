package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

object ReloadDatapacksC2SPacket : CustomPayload {
    val ID = CustomPayload.Id<ReloadDatapacksC2SPacket>(Identifier.of("command_crafter", "reload_datapacks"))
    val CODEC: PacketCodec<ByteBuf, ReloadDatapacksC2SPacket> = PacketCodec.unit(ReloadDatapacksC2SPacket)
    val TYPE: CustomPayload.Type<in RegistryByteBuf, ReloadDatapacksC2SPacket> = PayloadTypeRegistry.playC2S().register(ID, CODEC)

    override fun getId() = ID
}