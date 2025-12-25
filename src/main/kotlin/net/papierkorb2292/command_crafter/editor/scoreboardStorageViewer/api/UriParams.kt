package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

class UriParams(
    var uri: String
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, UriParams> = ByteBufCodecs.STRING_UTF8.map(
            ::UriParams,
            UriParams::uri
        )
    }

    constructor() : this("")
}