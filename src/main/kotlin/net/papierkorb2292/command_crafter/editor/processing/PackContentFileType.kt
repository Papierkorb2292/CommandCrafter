package net.papierkorb2292.command_crafter.editor.processing

import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import java.nio.file.Path
import java.util.*
import kotlin.io.path.name

data class PackContentFileType(val datapackCategory: String) {
    companion object {
        val FunctionsFileType = PackContentFileType("functions")

        fun parsePath(path: Path): ParsedPath? {
            val root = path.root
            for(i in 0 until path.nameCount - 2) {
                if(path.getName(i).toString() != "data") continue
                val subpath = if(i == 0) { root } else { root.resolve(path.subpath(0, i)) }
                if(subpath.toFile().isDirectory && Arrays.stream(subpath.toFile().listFiles()).anyMatch { it.name == "pack.mcmeta" }) {
                    val id = Identifier.of(
                        path.getName(i + 1).name,
                        path.subpath(i + 3, path.nameCount).name.replace('\\', '/')
                    ) ?: continue
                    return ParsedPath(
                        id,
                        PackContentFileType(path.getName(i + 2).name)
                    )
                }
            }
            return null
        }
    }

    constructor(buf: PacketByteBuf) : this(buf.readString())

    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeString(datapackCategory)
    }

    fun toPath(id: Identifier): Path {
        return Path.of(id.namespace, datapackCategory, id.path)
    }

    data class ParsedPath(val id: Identifier, val type: PackContentFileType)
}