package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.papierkorb2292.command_crafter.networking.array

class FileSystemWatchParams(
    var uri: String,
    var watcherId: Int,
    var recursive: Boolean,
    var excludes: Array<String>
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, FileSystemWatchParams> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            FileSystemWatchParams::uri,
            ByteBufCodecs.INT,
            FileSystemWatchParams::watcherId,
            ByteBufCodecs.BOOL,
            FileSystemWatchParams::recursive,
            ByteBufCodecs.STRING_UTF8.array(),
            FileSystemWatchParams::excludes,
            ::FileSystemWatchParams
        )
    }

    constructor() : this("", 0, false, arrayOf())
}