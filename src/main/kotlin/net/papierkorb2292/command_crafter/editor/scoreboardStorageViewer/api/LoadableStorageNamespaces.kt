package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.papierkorb2292.command_crafter.networking.array

class LoadableStorageNamespaces(val namespaces: Array<String>) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, LoadableStorageNamespaces> = PacketCodec.tuple(
            PacketCodecs.STRING.array(),
            LoadableStorageNamespaces::namespaces,
            ::LoadableStorageNamespaces
        )
    }
}