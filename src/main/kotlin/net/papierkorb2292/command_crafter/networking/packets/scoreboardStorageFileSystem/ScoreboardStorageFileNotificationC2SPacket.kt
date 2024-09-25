package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.*

class ScoreboardStorageFileNotificationC2SPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileNotificationC2SPacket<TParams>>, val params: TParams) : CustomPayload {
    companion object {
        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<ByteBuf, TParams>,
        ): (TParams) -> ScoreboardStorageFileNotificationC2SPacket<TParams> {
            val payloadId = CustomPayload.Id<ScoreboardStorageFileNotificationC2SPacket<TParams>>(packetId)
            val codec = PacketCodec.tuple(
                paramsCodec,
                ScoreboardStorageFileNotificationC2SPacket<TParams>::params
            ) { params: TParams ->
                ScoreboardStorageFileNotificationC2SPacket(
                    payloadId,
                    params
                )
            }
            PayloadTypeRegistry.playS2C().register(payloadId, codec)
            return { params -> ScoreboardStorageFileNotificationC2SPacket(payloadId, params) }
        }
    }

    override fun getId() = packetId
}