package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

@JvmInline
value class FilePermission(val value: Int) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, FilePermission> = ByteBufCodecs.INT.map(
            ::FilePermission,
            FilePermission::value
        )
        val READONLY = FilePermission(1)
    }
}