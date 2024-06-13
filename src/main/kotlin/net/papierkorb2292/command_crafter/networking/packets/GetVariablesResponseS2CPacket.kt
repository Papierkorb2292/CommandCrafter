package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.VARIABLE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.array
import org.eclipse.lsp4j.debug.Variable
import java.util.*

class GetVariablesResponseS2CPacket(val requestId: UUID, val variables: Array<Variable>): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<GetVariablesResponseS2CPacket>(Identifier.of("command_crafter", "get_variables_response"))
        val CODEC: PacketCodec<ByteBuf, GetVariablesResponseS2CPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            GetVariablesResponseS2CPacket::requestId,
            VARIABLE_PACKET_CODEC.array(),
            GetVariablesResponseS2CPacket::variables,
            ::GetVariablesResponseS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, GetVariablesResponseS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}