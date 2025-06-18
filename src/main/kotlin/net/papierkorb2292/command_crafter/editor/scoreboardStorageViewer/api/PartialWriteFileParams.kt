package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class PartialWriteFileParams(
    var uri: String,
    var contentBase64: String,
    val isLastPart: Boolean,
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, PartialWriteFileParams> = PacketCodec.tuple(
            PacketCodecs.STRING,
            PartialWriteFileParams::uri,
            PacketCodecs.STRING,
            PartialWriteFileParams::contentBase64,
            PacketCodecs.BOOLEAN,
            PartialWriteFileParams::isLastPart,
            ::PartialWriteFileParams
        )
    }
}