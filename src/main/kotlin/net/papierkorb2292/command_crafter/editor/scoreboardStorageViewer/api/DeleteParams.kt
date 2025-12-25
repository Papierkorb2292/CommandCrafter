package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

class DeleteParams(
    var uri: String,
    var recursive: Boolean
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, DeleteParams> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            DeleteParams::uri,
            ByteBufCodecs.BOOL,
            DeleteParams::recursive,
            ::DeleteParams
        )
    }

    constructor() : this("", false)
}