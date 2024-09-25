package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class ScoreboardStorageFileRequestC2SPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileRequestC2SPacket<TParams>>, val fileSystemId: UUID, val requestId: UUID, val params: TParams) : CustomPayload {
    companion object {
        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<ByteBuf, TParams>,
        ): (UUID, UUID, TParams) -> ScoreboardStorageFileRequestC2SPacket<TParams> {
            val payloadId = CustomPayload.Id<ScoreboardStorageFileRequestC2SPacket<TParams>>(packetId)
            val codec = PacketCodec.tuple(
                Uuids.PACKET_CODEC,
                ScoreboardStorageFileRequestC2SPacket<TParams>::fileSystemId,
                Uuids.PACKET_CODEC,
                ScoreboardStorageFileRequestC2SPacket<TParams>::requestId,
                paramsCodec,
                ScoreboardStorageFileRequestC2SPacket<TParams>::params
            ) { fileSystemId: UUID, requestId: UUID, params: TParams ->
                ScoreboardStorageFileRequestC2SPacket(
                    payloadId,
                    fileSystemId,
                    requestId,
                    params
                )
            }
            PayloadTypeRegistry.playS2C().register(payloadId, codec)
            return { fileSystemId, requestId, params -> ScoreboardStorageFileRequestC2SPacket(payloadId, fileSystemId, requestId, params) }
        }
    }

    override fun getId() = packetId
}