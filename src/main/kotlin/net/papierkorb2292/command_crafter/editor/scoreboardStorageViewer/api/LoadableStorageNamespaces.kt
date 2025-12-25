package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.papierkorb2292.command_crafter.networking.array

class LoadableStorageNamespaces(val namespaces: Array<String>) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, LoadableStorageNamespaces> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.array(),
            LoadableStorageNamespaces::namespaces,
            ::LoadableStorageNamespaces
        )
    }
}