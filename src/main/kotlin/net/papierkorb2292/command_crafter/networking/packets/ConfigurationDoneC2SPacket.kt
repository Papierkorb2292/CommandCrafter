package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import java.util.*

class ConfigurationDoneC2SPacket(val debugConnectionId: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ConfigurationDoneC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debugger_configuration_done"))
        val CODEC: StreamCodec<ByteBuf, ConfigurationDoneC2SPacket> = UUIDUtil.STREAM_CODEC.map(
            ::ConfigurationDoneC2SPacket,
            ConfigurationDoneC2SPacket::debugConnectionId
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, ConfigurationDoneC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}