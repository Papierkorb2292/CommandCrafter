package net.papierkorb2292.command_crafter.editor

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.removeExtension
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension

data class PackagedId(
    val resourceId: Identifier,
    val packPath: String
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, PackagedId> = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            PackagedId::resourceId,
            ByteBufCodecs.STRING_UTF8,
            PackagedId::packPath,
            ::PackagedId
        )

        private val POTENTIAL_PACK_ID_PREFIX = "file/"
        fun getPackIdWithoutPrefix(packId: String): String {
            return if(packId.startsWith(POTENTIAL_PACK_ID_PREFIX))
                packId.substring(POTENTIAL_PACK_ID_PREFIX.length)
            else
                packId
        }
    }

    fun removeExtension(extension: String) =
        resourceId.removeExtension(extension)?.let { forId(it) }

    fun withExtension(extension: String) =
        forId(resourceId.withExtension(extension))

    fun forId(resourceId: Identifier) = PackagedId(resourceId, packPath)
    fun forPackPath(packPath: String) = PackagedId(resourceId, packPath)

    override fun toString() = "$packPath@$resourceId"
}