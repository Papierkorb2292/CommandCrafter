package net.papierkorb2292.command_crafter.networking.packets

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import java.util.*

class InitializeNetworkServerConnectionS2CPacket(
    val successful: Boolean,
    val failReason: String?,
    val commandTree: ClientboundCommandsPacket,
    val functionPermissionLevel: Int,
    val requestId: UUID,
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<InitializeNetworkServerConnectionS2CPacket>(
            Identifier.fromNamespaceAndPath("command_crafter", "initialize_network_server_connection")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, InitializeNetworkServerConnectionS2CPacket> = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            InitializeNetworkServerConnectionS2CPacket::successful,
            ByteBufCodecs.STRING_UTF8.optional(),
            InitializeNetworkServerConnectionS2CPacket::failReason.toOptional(),
            ClientboundCommandsPacket.STREAM_CODEC,
            InitializeNetworkServerConnectionS2CPacket::commandTree,
            ByteBufCodecs.VAR_INT,
            InitializeNetworkServerConnectionS2CPacket::functionPermissionLevel,
            UUIDUtil.STREAM_CODEC,
            InitializeNetworkServerConnectionS2CPacket::requestId,
        ) { successful, failReason, commandTree, functionPermissionLevel, requestId ->
            InitializeNetworkServerConnectionS2CPacket(
                successful,
                failReason.orElse(null),
                commandTree,
                functionPermissionLevel,
                requestId
            )
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, InitializeNetworkServerConnectionS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}