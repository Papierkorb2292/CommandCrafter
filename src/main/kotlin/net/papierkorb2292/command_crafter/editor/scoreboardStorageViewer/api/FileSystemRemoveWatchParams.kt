package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class FileSystemRemoveWatchParams(
    var watcherId: Int
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, FileSystemRemoveWatchParams> = PacketCodecs.INTEGER.xmap(
            ::FileSystemRemoveWatchParams,
            FileSystemRemoveWatchParams::watcherId
        )
    }
    constructor() : this(0)
}