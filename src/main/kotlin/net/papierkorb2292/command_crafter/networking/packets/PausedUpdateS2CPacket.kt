package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.networking.STOPPED_EVENT_ARGUMENTS_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import org.eclipse.lsp4j.debug.StoppedEventArguments
import java.util.*

class PausedUpdateS2CPacket(val editorDebugConnection: UUID, val pause: Pair<UUID, StoppedEventArguments>?):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PausedUpdateS2CPacket>(Identifier.fromNamespaceAndPath("command_crafter", "debugger_paused_update"))
        val PAUSE_PAIR_PACKET_CODEC: StreamCodec<ByteBuf, Pair<UUID, StoppedEventArguments>> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            { it.first },
            STOPPED_EVENT_ARGUMENTS_PACKET_CODEC,
            { it.second },
            ::Pair
        )
        val CODEC: StreamCodec<ByteBuf, PausedUpdateS2CPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            PausedUpdateS2CPacket::editorDebugConnection,
            PAUSE_PAIR_PACKET_CODEC.optional(),
            PausedUpdateS2CPacket::pause.toOptional(),
        ) { editorDebugConnection, pause ->
            PausedUpdateS2CPacket(
                editorDebugConnection,
                pause.orElse(null)
            )
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, PausedUpdateS2CPacket> =
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
    }

    override fun type() = ID
}