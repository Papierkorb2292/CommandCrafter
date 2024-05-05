package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.networking.nullable
import java.util.*

class SetVariableResponseS2CPacket(val requestId: UUID, val response: VariablesReferencer.SetVariableResult?): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SetVariableResponseS2CPacket>(Identifier("command_crafter", "set_variable_response"))
        val CODEC: PacketCodec<ByteBuf, SetVariableResponseS2CPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            SetVariableResponseS2CPacket::requestId,
            VariablesReferencer.SetVariableResult.PACKET_CODEC.nullable(),
            SetVariableResponseS2CPacket::response,
            ::SetVariableResponseS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, SetVariableResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}