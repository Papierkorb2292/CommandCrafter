package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.Uuids
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileSystemRemoveWatchParams
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileSystemWatchParams
import java.util.*

class ScoreboardStorageFileNotificationC2SPacket<TParams>(private val packetId: CustomPayload.Id<ScoreboardStorageFileNotificationC2SPacket<TParams>>, val fileSystemId: UUID, val params: TParams) : CustomPayload {
    companion object {
        val ADD_WATCH_PACKET = createType(
            Identifier.of("command_crafter", "scoreboard_storage_file_add_watch"),
            FileSystemWatchParams.PACKET_CODEC
        )
        val REMOVE_WATCH_PACKET = createType(
            Identifier.of("command_crafter", "scoreboard_storage_file_remove_watch"),
            FileSystemRemoveWatchParams.PACKET_CODEC
        )

        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: PacketCodec<ByteBuf, TParams>,
        ): Type<TParams> {
            val payloadId = CustomPayload.Id<ScoreboardStorageFileNotificationC2SPacket<TParams>>(packetId)
            val codec = PacketCodec.tuple(
                Uuids.PACKET_CODEC,
                ScoreboardStorageFileNotificationC2SPacket<TParams>::fileSystemId,
                paramsCodec,
                ScoreboardStorageFileNotificationC2SPacket<TParams>::params
            ) { fileSystemId: UUID, params: TParams ->
                ScoreboardStorageFileNotificationC2SPacket(
                    payloadId,
                    fileSystemId,
                    params
                )
            }
            PayloadTypeRegistry.playC2S().register(payloadId, codec)
            return Type(payloadId) { fileSystemId, params -> ScoreboardStorageFileNotificationC2SPacket(payloadId, fileSystemId, params) }
        }
    }

    override fun getId() = packetId

    class Type<TParams>(val id: CustomPayload.Id<ScoreboardStorageFileNotificationC2SPacket<TParams>>, val factory: (fileSystemId: UUID, TParams) -> ScoreboardStorageFileNotificationC2SPacket<TParams>)
}