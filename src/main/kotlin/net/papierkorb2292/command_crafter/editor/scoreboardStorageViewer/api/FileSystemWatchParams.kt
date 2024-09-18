package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.papierkorb2292.command_crafter.networking.array

class FileSystemWatchParams(
    var uri: String,
    var watcherId: Int,
    var recursive: Boolean,
    var excludes: Array<String>
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, FileSystemWatchParams> = PacketCodec.tuple(
            PacketCodecs.STRING,
            FileSystemWatchParams::uri,
            PacketCodecs.INTEGER,
            FileSystemWatchParams::watcherId,
            PacketCodecs.BOOL,
            FileSystemWatchParams::recursive,
            PacketCodecs.STRING.array(),
            FileSystemWatchParams::excludes,
            ::FileSystemWatchParams
        )
    }

    constructor() : this("", 0, false, arrayOf())
}