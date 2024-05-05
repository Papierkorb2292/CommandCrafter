package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

class LogMessageS2CPacket(val logMessage: String): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<LogMessageS2CPacket>(Identifier("command_crafter", "log_message"))
        val CODEC: PacketCodec<ByteBuf, LogMessageS2CPacket> = PacketCodecs.STRING.xmap(
            ::LogMessageS2CPacket,
            LogMessageS2CPacket::logMessage
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, LogMessageS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }
    override fun getId() = ID
}