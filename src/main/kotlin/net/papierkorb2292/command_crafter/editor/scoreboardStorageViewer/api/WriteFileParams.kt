package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class WriteFileParams(
    var uri: String,
    var contentBase64: String
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, WriteFileParams> = PacketCodec.tuple(
            PacketCodecs.STRING,
            WriteFileParams::uri,
            PacketCodecs.STRING,
            WriteFileParams::contentBase64,
            ::WriteFileParams
        )
    }

    constructor() : this("", "")
}