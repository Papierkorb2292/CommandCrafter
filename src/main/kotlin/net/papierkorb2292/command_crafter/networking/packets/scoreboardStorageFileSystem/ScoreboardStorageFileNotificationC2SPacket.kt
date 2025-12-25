package net.papierkorb2292.command_crafter.networking.packets.scoreboardStorageFileSystem

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileSystemRemoveWatchParams
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileSystemWatchParams
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.LoadStorageNamespaceParams
import java.util.*

class ScoreboardStorageFileNotificationC2SPacket<TParams>(private val packetId: CustomPacketPayload.Type<ScoreboardStorageFileNotificationC2SPacket<TParams>>, val fileSystemId: UUID, val params: TParams) :
    CustomPacketPayload {
    companion object {
        val ADD_WATCH_PACKET = createType(
            Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_add_watch"),
            FileSystemWatchParams.PACKET_CODEC
        )
        val REMOVE_WATCH_PACKET = createType(
            Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_remove_watch"),
            FileSystemRemoveWatchParams.PACKET_CODEC
        )
        val LOAD_STORAGE_NAMESPACE_PACKET = createType(
            Identifier.fromNamespaceAndPath("command_crafter", "scoreboard_storage_file_load_storage_namespace"),
            LoadStorageNamespaceParams.PACKET_CODEC
        )

        fun <TParams : Any> createType(
            packetId: Identifier,
            paramsCodec: StreamCodec<ByteBuf, TParams>,
        ): Type<TParams> {
            val payloadId = CustomPacketPayload.Type<ScoreboardStorageFileNotificationC2SPacket<TParams>>(packetId)
            val codec = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
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

    override fun type() = packetId

    class Type<TParams>(val id: CustomPacketPayload.Type<ScoreboardStorageFileNotificationC2SPacket<TParams>>, val factory: (fileSystemId: UUID, TParams) -> ScoreboardStorageFileNotificationC2SPacket<TParams>)
}