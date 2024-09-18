package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class RenameParams(
    var oldUri: String,
    var newUri: String,
    var overwrite: Boolean
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, RenameParams> = PacketCodec.tuple(
            PacketCodecs.STRING,
            RenameParams::oldUri,
            PacketCodecs.STRING,
            RenameParams::newUri,
            PacketCodecs.BOOL,
            RenameParams::overwrite,
            ::RenameParams
        )
    }

    constructor() : this("", "", false)
}