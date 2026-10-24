package net.papierkorb2292.command_crafter.editor.files

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

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