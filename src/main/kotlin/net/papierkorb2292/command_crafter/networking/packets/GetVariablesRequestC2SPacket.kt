package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.VARIABLES_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.*

class GetVariablesRequestC2SPacket(val pauseId: UUID, val requestId: UUID, val args: VariablesArguments):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<GetVariablesRequestC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "get_variables_request"))
        val CODEC: StreamCodec<ByteBuf, GetVariablesRequestC2SPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            GetVariablesRequestC2SPacket::pauseId,
            UUIDUtil.STREAM_CODEC,
            GetVariablesRequestC2SPacket::requestId,
            VARIABLES_ARGUMENTS_PACKET_CODEC,
            GetVariablesRequestC2SPacket::args,
            ::GetVariablesRequestC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, GetVariablesRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}