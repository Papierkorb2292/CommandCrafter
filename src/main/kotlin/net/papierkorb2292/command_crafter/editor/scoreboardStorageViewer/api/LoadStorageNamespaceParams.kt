package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

data class LoadStorageNamespaceParams(val namespace: String) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, LoadStorageNamespaceParams> = PacketCodec.tuple(
            PacketCodecs.STRING,
            LoadStorageNamespaceParams::namespace,
            ::LoadStorageNamespaceParams
        )
    }
}