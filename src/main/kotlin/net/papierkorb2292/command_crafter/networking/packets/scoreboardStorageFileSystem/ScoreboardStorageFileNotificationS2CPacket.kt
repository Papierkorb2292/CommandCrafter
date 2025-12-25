package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FILE_EVENT_ARRAY_PACKET_CODEC
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileEvent
import java.util.*

class ScoreboardStorageFileNotificationS2CPacket<TParams>(private val packetId: CustomPacketPayload.Type<ScoreboardStorageFileNotificationS2CPacket<TParams>>, val fileSystemId: UUID, val params: TParams) :
    CustomPacketPayload {
    companion object {
        val DID_CHANGE_FILE_PACKET: Type<Array<FileEvent>> = createType(
            Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_did_change"),
            FILE_EVENT_ARRAY_PACKET_CODEC
        )

        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: StreamCodec<ByteBuf, TParams>,
        ): Type<TParams> {
            val payloadId = CustomPacketPayload.Type<ScoreboardStorageFileNotificationS2CPacket<TParams>>(packetId)
            val codec = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
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

    override fun type() = packetId

    class Type<TParams>(val id: CustomPacketPayload.Type<ScoreboardStorageFileNotificationS2CPacket<TParams>>, val factory: (fileSystemId: UUID, TParams) -> ScoreboardStorageFileNotificationS2CPacket<TParams>)
}