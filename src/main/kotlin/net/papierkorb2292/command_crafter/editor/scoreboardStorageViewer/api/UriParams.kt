package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

class UriParams(
    var uri: String
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, UriParams> = PacketCodecs.STRING.xmap(
            ::UriParams,
            UriParams::uri
        )
    }

    constructor() : this("")
}