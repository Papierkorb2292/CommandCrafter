package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.EXITED_EVENT_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.ExitedEventArguments
import java.util.*

class DebuggerExitS2CPacket(val args: ExitedEventArguments, val editorDebugConnection: UUID): CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DebuggerExitS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debugger_exit"))
        val CODEC: StreamCodec<ByteBuf, DebuggerExitS2CPacket> = StreamCodec.composite(
            EXITED_EVENT_ARGUMENTS_PACKET_CODEC,
            DebuggerExitS2CPacket::args,
            UUIDUtil.STREAM_CODEC,
            DebuggerExitS2CPacket::editorDebugConnection,
            ::DebuggerExitS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, DebuggerExitS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}