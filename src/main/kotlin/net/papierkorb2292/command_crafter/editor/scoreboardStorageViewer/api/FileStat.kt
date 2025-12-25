package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional

class FileStat(
    /**
     * The type of the file, e.g. is a regular file, a directory, or symbolic link
     * to a file.
     *
     * *Note:* This value might be a bitmask, e.g. `FileType.File | FileType.SymbolicLink`.
     */
    var type: FileType,
    /**
     * The creation timestamp in milliseconds elapsed since January 1, 1970 00:00:00 UTC.
     */
    var ctime: Int,
    /**
     * The modification timestamp in milliseconds elapsed since January 1, 1970 00:00:00 UTC.
     *
     * *Note:* If the file changed, it is important to provide an updated `mtime` that advanced
     * from the previous value. Otherwise there may be optimizations in place that will not show
     * the updated file contents in an editor for example.
     */
    var mtime: Int,
    /**
     * The size in bytes.
     *
     * *Note:* If the file changed, it is important to provide an updated `size`. Otherwise there
     * may be optimizations in place that will not show the updated file contents in an editor for
     * example.
     */
    var size: Int,
    /**
     * The permissions of the file, e.g. whether the file is readonly.
     *
     * *Note:* This value might be a bitmask, e.g. `FilePermission.Readonly | FilePermission.Other`.
     */
    var permissions: FilePermission? = null
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, FileStat> = StreamCodec.composite(
            FileType.PACKET_CODEC,
            FileStat::type,
            ByteBufCodecs.INT,
            FileStat::ctime,
            ByteBufCodecs.INT,
            FileStat::mtime,
            ByteBufCodecs.INT,
            FileStat::size,
            FilePermission.PACKET_CODEC.optional(),
            FileStat::permissions.toOptional(),
        ) { type, ctime, mtime, size, permissions ->
            FileStat(
                type,
                ctime,
                mtime,
                size,
                permissions.orElse(null)
            )
        }
    }
}