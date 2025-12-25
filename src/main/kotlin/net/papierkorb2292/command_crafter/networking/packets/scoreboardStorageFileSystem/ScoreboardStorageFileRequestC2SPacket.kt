package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.networking.UNIT_CODEC
import java.util.*

class ScoreboardStorageFileRequestC2SPacket<TParams>(private val packetId: CustomPacketPayload.Type<ScoreboardStorageFileRequestC2SPacket<TParams>>, val fileSystemId: UUID, val requestId: UUID, val params: TParams) :
    CustomPacketPayload {
    companion object {
        val STAT_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_stat_request"), UriParams.PACKET_CODEC)
        val READ_DIRECTORY_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_read_directory_request"), UriParams.PACKET_CODEC)
        val CREATE_DIRECTORY_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_create_directory_request"), UriParams.PACKET_CODEC)
        val READ_FILE_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_read_file_request"), UriParams.PACKET_CODEC)
        val WRITE_FILE_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_write_file_request"), PartialWriteFileParams.PACKET_CODEC)
        val DELETE_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_delete_request"), DeleteParams.PACKET_CODEC)
        val RENAME_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_rename_request"), RenameParams.PACKET_CODEC)
        val LOADABLE_STORAGE_NAMESPACES_PACKET = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_get_loadable_storage_namespaces_request"), UNIT_CODEC)

        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: StreamCodec<ByteBuf, TParams>,
        ): Type<TParams> {
            val payloadId = CustomPacketPayload.Type<ScoreboardStorageFileRequestC2SPacket<TParams>>(packetId)
            val codec = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                ScoreboardStorageFileRequestC2SPacket<TParams>::fileSystemId,
                UUIDUtil.STREAM_CODEC,
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
            PayloadTypeRegistry.playC2S().register(payloadId, codec)
            return Type(payloadId) { fileSystemId, requestId, params -> ScoreboardStorageFileRequestC2SPacket(payloadId, fileSystemId, requestId, params) }
        }
    }

    override fun type() = packetId

    class Type<TParams>(val id: CustomPacketPayload.Type<ScoreboardStorageFileRequestC2SPacket<TParams>>, val factory: (fileSystemId: UUID, requestId: UUID, TParams) -> ScoreboardStorageFileRequestC2SPacket<TParams>)
}