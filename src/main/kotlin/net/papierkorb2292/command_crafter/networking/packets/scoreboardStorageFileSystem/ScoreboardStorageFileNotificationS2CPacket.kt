package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FILE_EVENT_ARRAY_PACKET_CODEC
import java.util.*

class ScoreboardStorageFileNotificationS2CPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileNotificationS2CPacket<TParams>>, val fileSystemId: UUID, val params: TParams) :
    CustomPayload {
    companion object {
        val DID_CHANGE_FILE_PACKET = createType(
            Identifier.of("command_crafter", "scoreboard_storage_file_did_change"),
            FILE_EVENT_ARRAY_PACKET_CODEC
        )

        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<ByteBuf, TParams>,
        ): Type<TParams> {
            val payloadId = CustomPayload.Id<ScoreboardStorageFileNotificationS2CPacket<TParams>>(packetId)
            val codec = PacketCodec.tuple(
                Uuids.PACKET_CODEC,
                ScoreboardStorageFileNotificationS2CPacket<TParams>::fileSystemId,
                paramsCodec,
                ScoreboardStorageFileNotificationS2CPacket<TParams>::params
            ) { fileSystemId: UUID, params: TParams ->
                ScoreboardStorageFileNotificationS2CPacket(
                    payloadId,
                    fileSystemId,
                    params
                )
            }
            PayloadTypeRegistry.playS2C().register(payloadId, codec)
            return Type(payloadId) { fileSystemId, params -> ScoreboardStorageFileNotificationS2CPacket(payloadId, fileSystemId, params) }
        }
    }

    override fun getId() = packetId

    class Type<TParams>(val id: CustomPayload.Id<ScoreboardStorageFileNotificationS2CPacket<TParams>>, val factory: (fileSystemId: UUID, TParams) -> ScoreboardStorageFileNotificationS2CPacket<TParams>)
}