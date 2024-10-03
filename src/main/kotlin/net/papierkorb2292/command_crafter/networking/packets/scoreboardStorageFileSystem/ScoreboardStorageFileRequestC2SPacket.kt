package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import java.util.*

class ScoreboardStorageFileRequestC2SPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileRequestC2SPacket<TParams>>, val fileSystemId: UUID, val requestId: UUID, val params: TParams) : CustomPayload {
    companion object {
        val STAT_PACKET = createType(Identifier.of("command_crafter", "scoreboard_storage_file_stat_request"), UriParams.PACKET_CODEC)
        val READ_DIRECTORY_PACKET = createType(Identifier.of("command_crafter", "scoreboard_storage_file_read_directory_request"), UriParams.PACKET_CODEC)
        val CREATE_DIRECTORY_PACKET = createType(Identifier.of("command_crafter", "scoreboard_storage_file_create_directory_request"), UriParams.PACKET_CODEC)
        val READ_FILE_PACKET = createType(Identifier.of("command_crafter", "scoreboard_storage_file_read_file_request"), UriParams.PACKET_CODEC)
        val WRITE_FILE_PACKET = createType(Identifier.of("command_crafter", "scoreboard_storage_file_write_file_request"), WriteFileParams.PACKET_CODEC)
        val DELETE_PACKET = createType(Identifier.of("command_crafter", "scoreboard_storage_file_delete_request"), DeleteParams.PACKET_CODEC)
        val RENAME_PACKET = createType(Identifier.of("command_crafter", "scoreboard_storage_file_rename_request"), RenameParams.PACKET_CODEC)

        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<ByteBuf, TParams>,
        ): Type<TParams> {
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
            return Type(payloadId) { fileSystemId, requestId, params -> ScoreboardStorageFileRequestC2SPacket(payloadId, fileSystemId, requestId, params) }
        }
    }

    override fun getId() = packetId

    class Type<TParams>(val id: CustomPayload.Id<ScoreboardStorageFileRequestC2SPacket<TParams>>, val factory: (fileSystemId: UUID, requestId: UUID, TParams) -> ScoreboardStorageFileRequestC2SPacket<TParams>)
}