package net.papierkorb2292.command_crafter.editor.files

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

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

    fun withSymbolicLink(isSymbolLink: Boolean): FileType {
        return if (isSymbolLink) {
            FileType(value or SYMBOLIC_LINK.value)
        } else {
            FileType(value and SYMBOLIC_LINK.value.inv())
        }
    }

    fun hasFlag(flag: FileType): Boolean {
        return (value and flag.value) == flag.value
    }
}