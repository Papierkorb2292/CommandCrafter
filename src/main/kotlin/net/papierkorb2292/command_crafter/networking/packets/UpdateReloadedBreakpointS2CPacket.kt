package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.BREAKPOINT_EVENT_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import java.util.*

class UpdateReloadedBreakpointS2CPacket(val update: BreakpointEventArguments, val editorDebugConnection: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<UpdateReloadedBreakpointS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "update_reloaded_breakpoint"))
        val CODEC: StreamCodec<ByteBuf, UpdateReloadedBreakpointS2CPacket> = StreamCodec.composite(
            BREAKPOINT_EVENT_ARGUMENTS_PACKET_CODEC,
            UpdateReloadedBreakpointS2CPacket::update,
            UUIDUtil.STREAM_CODEC,
            UpdateReloadedBreakpointS2CPacket::editorDebugConnection,
            ::UpdateReloadedBreakpointS2CPacket
        )
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, UpdateReloadedBreakpointS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}