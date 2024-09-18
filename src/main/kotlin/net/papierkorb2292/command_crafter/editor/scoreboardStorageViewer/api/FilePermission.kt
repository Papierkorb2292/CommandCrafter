package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs

@JvmInline
value class FilePermission(val value: Int) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, FilePermission> = PacketCodecs.INTEGER.xmap(
            ::FilePermission,
            FilePermission::value
        )
        val READONLY = FilePermission(1)
    }
}