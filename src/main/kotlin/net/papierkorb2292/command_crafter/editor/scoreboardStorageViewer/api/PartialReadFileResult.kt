package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

class PartialReadFileResult(
    var contentBase64: String,
    var isLastPart: Boolean,
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, PartialReadFileResult> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PartialReadFileResult::contentBase64,
            ByteBufCodecs.BOOL,
            PartialReadFileResult::isLastPart,
            ::PartialReadFileResult
        )
    }
}