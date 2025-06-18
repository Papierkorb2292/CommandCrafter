package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class PartialReadFileResult(
    var contentBase64: String,
    var isLastPart: Boolean,
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, PartialReadFileResult> = PacketCodec.tuple(
            PacketCodecs.STRING,
            PartialReadFileResult::contentBase64,
            PacketCodecs.BOOLEAN,
            PartialReadFileResult::isLastPart,
            ::PartialReadFileResult
        )
    }
}