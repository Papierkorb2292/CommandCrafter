package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.networking.UNIT_CODEC
import net.papierkorb2292.command_crafter.networking.array
import java.util.*

class ScoreboardStorageFileResponseS2CPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileResponseS2CPacket<TParams>>, val requestId: UUID, val params: TParams) : CustomPayload {
    companion object {
        val STAT_RESPONSE_PACKET: Type<FileSystemResult<FileStat>> = createType(Identifier.of("command_crafter", "scoreboard_storage_file_stat_response"), FileSystemResult.createCodec(FileStat.PACKET_CODEC))
        val READ_DIRECTORY_RESPONSE_PACKET: Type<FileSystemResult<Array<ReadDirectoryResultEntry>>> = createType(Identifier.of("command_crafter", "scoreboard_storage_file_read_directory_response"), FileSystemResult.createCodec(ReadDirectoryResultEntry.PACKET_CODEC.array()))
        val CREATE_DIRECTORY_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.of("command_crafter", "scoreboard_storage_file_create_directory_response"), FileSystemResult.createCodec(UNIT_CODEC))
        val READ_FILE_RESPONSE_PACKET: Type<FileSystemResult<ReadFileResult>> = createType(Identifier.of("command_crafter", "scoreboard_storage_file_read_file_response"), FileSystemResult.createCodec(ReadFileResult.PACKET_CODEC))
        val WRITE_FILE_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.of("command_crafter", "scoreboard_storage_file_write_file_response"), FileSystemResult.createCodec(UNIT_CODEC))
        val DELETE_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.of("command_crafter", "scoreboard_storage_file_delete_response"), FileSystemResult.createCodec(UNIT_CODEC))
        val RENAME_RESPONSE_PACKET: Type<FileSystemResult<Unit>> = createType(Identifier.of("command_crafter", "scoreboard_storage_file_rename_response"), FileSystemResult.createCodec(UNIT_CODEC))
        
        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<PacketByteBuf, TParams>,
        ): Type<TParams> {
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
            return Type(payloadId) { requestId, params -> ScoreboardStorageFileResponseS2CPacket(payloadId, requestId, params) }
        }
    }

    override fun getId() = packetId
    
    class Type<TParams>(val id: CustomPayload.Id<ScoreboardStorageFileResponseS2CPacket<TParams>>, val factory: (requestId: UUID, TParams) -> ScoreboardStorageFileResponseS2CPacket<TParams>)
}