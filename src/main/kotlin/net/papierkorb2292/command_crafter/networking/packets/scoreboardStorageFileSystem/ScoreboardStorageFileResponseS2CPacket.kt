package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.networking.UNIT_CODEC
import net.papierkorb2292.command_crafter.networking.array
import java.util.*

class ScoreboardStorageFileResponseS2CPacket<TParams>(private val packetId: CustomPacketPayload.Type<ScoreboardStorageFileResponseS2CPacket<TParams>>, val requestId: UUID, val params: TParams) :
    CustomPacketPayload {
    companion object {
        val STAT_RESPONSE_PACKET: Type<FileSystemResult<FileStat>> = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_stat_response"), FileSystemResult.createCodec(FileStat.PACKET_CODEC))
        val READ_DIRECTORY_RESPONSE_PACKET: Type<FileSystemResult<Array<ReadDirectoryResultEntry>>> = createType(
            Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_read_directory_response"), FileSystemResult.createCodec(ReadDirectoryResultEntry.PACKET_CODEC.array()))
        val CREATE_DIRECTORY_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_create_directory_response"), FileSystemResult.createCodec(UNIT_CODEC))
        val READ_FILE_RESPONSE_PACKET: Type<FileSystemResult<PartialReadFileResult>> = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_read_file_response"), FileSystemResult.createCodec(PartialReadFileResult.PACKET_CODEC))
        val WRITE_FILE_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_write_file_response"), FileSystemResult.createCodec(UNIT_CODEC))
        val DELETE_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_delete_response"), FileSystemResult.createCodec(UNIT_CODEC))
        val RENAME_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_rename_response"), FileSystemResult.createCodec(UNIT_CODEC))
        val LOADABLE_STORAGE_NAMESPACES_RESPONSE_PACKET: Type<LoadableStorageNamespaces> = createType(Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_get_loadable_storage_namespaces_response"), LoadableStorageNamespaces.PACKET_CODEC.cast())
        
        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: StreamCodec<FriendlyByteBuf, TParams>,
        ): Type<TParams> {
            val payloadId = CustomPacketPayload.Type<ScoreboardStorageFileResponseS2CPacket<TParams>>(packetId)
            val codec = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
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
            return Type(payloadId) { requestId, params -> ScoreboardStorageFileResponseS2CPacket(payloadId, requestId, params) }
        }
    }

    override fun type() = packetId
    
    class Type<TParams>(val id: CustomPacketPayload.Type<ScoreboardStorageFileResponseS2CPacket<TParams>>, val factory: (requestId: UUID, TParams) -> ScoreboardStorageFileResponseS2CPacket<TParams>)
}