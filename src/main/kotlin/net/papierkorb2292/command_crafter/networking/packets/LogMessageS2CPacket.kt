package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class LogMessageS2CPacket(val logMessage: String): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<LogMessageS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "log_message"))
        val CODEC: StreamCodec<ByteBuf, LogMessageS2CPacket> = ByteBufCodecs.STRING_UTF8.map(
            ::LogMessageS2CPacket,
            LogMessageS2CPacket::logMessage
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, LogMessageS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }
    override fun type() = ID
}