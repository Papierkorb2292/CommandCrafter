package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

class PartialWriteFileParams(
    var uri: String,
    var contentBase64: String,
    val isLastPart: Boolean,
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, PartialWriteFileParams> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PartialWriteFileParams::uri,
            ByteBufCodecs.STRING_UTF8,
            PartialWriteFileParams::contentBase64,
            ByteBufCodecs.BOOL,
            PartialWriteFileParams::isLastPart,
            ::PartialWriteFileParams
        )
    }
}