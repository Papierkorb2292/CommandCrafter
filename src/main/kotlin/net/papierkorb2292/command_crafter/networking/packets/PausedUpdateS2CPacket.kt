package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.networking.STOPPED_EVENT_ARGUMENTS_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.nullable
import org.eclipse.lsp4j.debug.StoppedEventArguments
import java.util.*

class PausedUpdateS2CPacket(val editorDebugConnection: UUID, val pause: Pair<UUID, StoppedEventArguments>?): CustomPayload {
    companion object {
        val ID = CustomPayload.Id<PausedUpdateS2CPacket>(Identifier("command_crafter", "debugger_paused_update"))
        val PAUSE_PAIR_PACKET_CODEC: PacketCodec<ByteBuf, Pair<UUID, StoppedEventArguments>> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            { it.first },
            STOPPED_EVENT_ARGUMENTS_PACKET_CODEC,
            { it.second },
            ::Pair
        )
        val CODEC: PacketCodec<ByteBuf, PausedUpdateS2CPacket> = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            PausedUpdateS2CPacket::editorDebugConnection,
            PAUSE_PAIR_PACKET_CODEC.nullable(),
            PausedUpdateS2CPacket::pause,
            ::PausedUpdateS2CPacket
        )
        val TYPE: CustomPayload.Type<in RegistryByteBuf, PausedUpdateS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun getId() = ID
}