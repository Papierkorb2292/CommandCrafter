package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.VARIABLES_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.*

class GetVariablesRequestC2SPacket(val pauseId: UUID, val requestId: UUID, val args: VariablesArguments): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<GetVariablesRequestC2SPacket>(Identifier.of("command_crafter", "get_variables_request"))
        val CODEC: PacketCodec<ByteBuf, GetVariablesRequestC2SPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            GetVariablesRequestC2SPacket::pauseId,
            Uuids.PACKET_CODEC,
            GetVariablesRequestC2SPacket::requestId,
            VARIABLES_ARGUMENTS_PACKET_CODEC,
            GetVariablesRequestC2SPacket::args,
            ::GetVariablesRequestC2SPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, GetVariablesRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun getId() = ID
}