package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class DeleteParams(
    var uri: String,
    var recursive: Boolean
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, DeleteParams> = PacketCodec.tuple(
            PacketCodecs.STRING,
            DeleteParams::uri,
            PacketCodecs.BOOLEAN,
            DeleteParams::recursive,
            ::DeleteParams
        )
    }

    constructor() : this("", false)
}