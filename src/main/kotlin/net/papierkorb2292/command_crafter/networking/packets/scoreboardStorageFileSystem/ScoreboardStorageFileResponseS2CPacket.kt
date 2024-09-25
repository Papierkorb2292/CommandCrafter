package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import java.util.*

class ScoreboardStorageFileResponseS2CPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileResponseS2CPacket<TParams>>, val requestId: UUID, val params: TParams) : CustomPayload {
    companion object {
        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<ByteBuf, TParams>,
        ): (UUID, TParams) -> ScoreboardStorageFileResponseS2CPacket<TParams> {
            val payloadId = CustomPayload.Id<ScoreboardStorageFileResponseS2CPacket<TParams>>(packetId)
            val codec = PacketCodec.tuple(
                Uuids.PACKET_CODEC,
                ScoreboardStorageFileResponseS2CPacket<TParams>::requestId,
                paramsCodec,
                ScoreboardStorageFileResponseS2CPacket<TParams>::params
            ) { requestId: UUID, params: TParams ->
                ScoreboardStorageFileResponseS2CPacket(
                    payloadId,
                    requestId,
                    params
                )
            }
            PayloadTypeRegistry.playS2C().register(payloadId, codec)
            return { requestId, params -> ScoreboardStorageFileResponseS2CPacket(payloadId, requestId, params) }
        }
    }

    override fun getId() = packetId
}