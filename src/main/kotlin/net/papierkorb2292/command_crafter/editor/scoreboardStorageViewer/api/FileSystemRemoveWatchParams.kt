package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

class FileSystemRemoveWatchParams(
    var watcherId: Int
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, FileSystemRemoveWatchParams> = ByteBufCodecs.INT.map(
            ::FileSystemRemoveWatchParams,
            FileSystemRemoveWatchParams::watcherId
        )
    }
    constructor() : this(0)
}