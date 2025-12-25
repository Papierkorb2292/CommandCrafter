package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import java.util.*

class SetVariableResponseS2CPacket(val requestId: UUID, val response: VariablesReferencer.SetVariableResult?):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SetVariableResponseS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "set_variable_response"))
        val CODEC: StreamCodec<ByteBuf, SetVariableResponseS2CPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            SetVariableResponseS2CPacket::requestId,
            VariablesReferencer.SetVariableResult.PACKET_CODEC.optional(),
            SetVariableResponseS2CPacket::response.toOptional(),
        ) { requestId, response ->
            SetVariableResponseS2CPacket(
                requestId,
                response.orElse(null)
            )
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, SetVariableResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}