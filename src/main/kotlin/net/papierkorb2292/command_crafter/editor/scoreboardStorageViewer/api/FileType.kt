package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

@JvmInline
value class FileType(val value: Int) {
    companion object {
        val UNKNOWN = FileType(0)
        val FILE = FileType(1)
        val DIRECTORY = FileType(2)
        val SYMBOLIC_LINK = FileType(64)

        val PACKET_CODEC: StreamCodec<ByteBuf, FileType> = ByteBufCodecs.INT.map(
            ::FileType,
            FileType::value
        )
    }
}