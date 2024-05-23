package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.SET_VARIABLE_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.SetVariableArguments
import java.util.*

class SetVariableRequestC2SPacket(val pauseId: UUID, val requestId: UUID, val args: SetVariableArguments): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<SetVariableRequestC2SPacket>(Identifier("command_crafter", "set_variable_request"))
        val CODEC: PacketCodec<ByteBuf, SetVariableRequestC2SPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            SetVariableRequestC2SPacket::pauseId,
            Uuids.PACKET_CODEC,
            SetVariableRequestC2SPacket::requestId,
            SET_VARIABLE_ARGUMENTS_PACKET_CODEC,
            SetVariableRequestC2SPacket::args,
            ::SetVariableRequestC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, SetVariableRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}