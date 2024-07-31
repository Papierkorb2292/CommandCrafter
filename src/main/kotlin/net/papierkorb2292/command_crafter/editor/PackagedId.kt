package net.papierkorb2292.command_crafter.editor

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.removeExtension
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension

data class PackagedId(
    val resourceId: Identifier,
    val packPath: String
) {
    companion object {
        val PACKET_CODEC: PacketCodec<ByteBuf, PackagedId> = PacketCodec.tuple(
            Identifier.PACKET_CODEC,
            PackagedId::resourceId,
            PacketCodecs.STRING,
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