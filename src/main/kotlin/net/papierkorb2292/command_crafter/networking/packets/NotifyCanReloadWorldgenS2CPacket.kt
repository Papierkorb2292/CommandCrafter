package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

class NotifyCanReloadWorldgenS2CPacket(
    val canReloadWorldgen: Boolean
): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<NotifyCanReloadWorldgenS2CPacket>(Identifier.of("command_crafter", "notify_can_reload_worldgen"))
        val CODEC: PacketCodec<PacketByteBuf, NotifyCanReloadWorldgenS2CPacket> = PacketCodec.tuple(
            PacketCodecs.BOOLEAN,
            NotifyCanReloadWorldgenS2CPacket::canReloadWorldgen,
            ::NotifyCanReloadWorldgenS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, NotifyCanReloadWorldgenS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}