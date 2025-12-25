package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.VARIABLE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.array
import org.eclipse.lsp4j.debug.Variable
import java.util.*

class GetVariablesResponseS2CPacket(val requestId: UUID, val variables: Array<Variable>): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<GetVariablesResponseS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "get_variables_response"))
        val CODEC: StreamCodec<ByteBuf, GetVariablesResponseS2CPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            GetVariablesResponseS2CPacket::requestId,
            VARIABLE_PACKET_CODEC.array(),
            GetVariablesResponseS2CPacket::variables,
            ::GetVariablesResponseS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, GetVariablesResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}