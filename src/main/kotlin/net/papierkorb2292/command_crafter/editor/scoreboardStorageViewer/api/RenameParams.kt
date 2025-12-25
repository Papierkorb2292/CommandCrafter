package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

class RenameParams(
    var oldUri: String,
    var newUri: String,
    var overwrite: Boolean
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, RenameParams> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            RenameParams::oldUri,
            ByteBufCodecs.STRING_UTF8,
            RenameParams::newUri,
            ByteBufCodecs.BOOL,
            RenameParams::overwrite,
            ::RenameParams
        )
    }

    constructor() : this("", "", false)
}