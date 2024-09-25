package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

class ScoreboardStorageFileNotificationS2CPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileNotificationS2CPacket<TParams>>, val params: TParams) :
    CustomPayload {
    companion object {
        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<ByteBuf, TParams>,
        ): (TParams) -> ScoreboardStorageFileNotificationS2CPacket<TParams> {
            val payloadId = CustomPayload.Id<ScoreboardStorageFileNotificationS2CPacket<TParams>>(packetId)
            val codec = PacketCodec.tuple(
                paramsCodec,
                ScoreboardStorageFileNotificationS2CPacket<TParams>::params
            ) { params: TParams ->
                ScoreboardStorageFileNotificationS2CPacket(
                    payloadId,
                    params
                )
            }
            PayloadTypeRegistry.playS2C().register(payloadId, codec)
            return { params -> ScoreboardStorageFileNotificationS2CPacket(payloadId, params) }
        }
    }

    override fun getId() = packetId
}