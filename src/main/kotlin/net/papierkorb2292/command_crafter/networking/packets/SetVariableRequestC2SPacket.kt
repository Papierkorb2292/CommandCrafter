package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.SET_VARIABLE_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.SetVariableArguments
import java.util.*

class SetVariableRequestC2SPacket(val pauseId: UUID, val requestId: UUID, val args: SetVariableArguments):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SetVariableRequestC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "set_variable_request"))
        val CODEC: StreamCodec<ByteBuf, SetVariableRequestC2SPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            SetVariableRequestC2SPacket::pauseId,
            UUIDUtil.STREAM_CODEC,
            SetVariableRequestC2SPacket::requestId,
            SET_VARIABLE_ARGUMENTS_PACKET_CODEC,
            SetVariableRequestC2SPacket::args,
            ::SetVariableRequestC2SPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, SetVariableRequestC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}