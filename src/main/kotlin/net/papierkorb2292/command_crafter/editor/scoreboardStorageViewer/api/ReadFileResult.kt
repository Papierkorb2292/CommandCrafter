package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class ReadFileResult(
    var contentBase64: String
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, ReadFileResult> = PacketCodec.tuple(
            PacketCodecs.STRING,
            ReadFileResult::contentBase64,
            ::ReadFileResult
        )
    }
}