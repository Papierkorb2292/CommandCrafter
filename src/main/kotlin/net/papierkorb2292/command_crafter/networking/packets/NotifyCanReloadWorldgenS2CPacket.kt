package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class NotifyCanReloadWorldgenS2CPacket(
    val canReloadWorldgen: Boolean
): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<NotifyCanReloadWorldgenS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "notify_can_reload_worldgen"))
        val CODEC: StreamCodec<FriendlyByteBuf, NotifyCanReloadWorldgenS2CPacket> = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            NotifyCanReloadWorldgenS2CPacket::canReloadWorldgen,
            ::NotifyCanReloadWorldgenS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, NotifyCanReloadWorldgenS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = ID
}