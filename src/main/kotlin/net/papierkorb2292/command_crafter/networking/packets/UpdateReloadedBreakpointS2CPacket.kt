package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.BREAKPOINT_EVENT_ARGUMENTS_PACKET_CODEC
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import java.util.*

class UpdateReloadedBreakpointS2CPacket(val update: BreakpointEventArguments, val editorDebugConnection: UUID): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<UpdateReloadedBreakpointS2CPacket>(Identifier("command_crafter", "update_reloaded_breakpoint"))
        val CODEC: PacketCodec<ByteBuf, UpdateReloadedBreakpointS2CPacket> = PacketCodec.tuple(
            BREAKPOINT_EVENT_ARGUMENTS_PACKET_CODEC,
            UpdateReloadedBreakpointS2CPacket::update,
            Uuids.PACKET_CODEC,
            UpdateReloadedBreakpointS2CPacket::editorDebugConnection,
            ::UpdateReloadedBreakpointS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, UpdateReloadedBreakpointS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}