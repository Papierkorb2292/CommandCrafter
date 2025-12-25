package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs

class ReadDirectoryResultEntry(
    var name: String,
    var fileType: FileType
) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, ReadDirectoryResultEntry> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ReadDirectoryResultEntry::name,
            FileType.PACKET_CODEC,
            ReadDirectoryResultEntry::fileType,
            ::ReadDirectoryResultEntry
        )
    }

    object TypeAdapter : com.google.gson.TypeAdapter<ReadDirectoryResultEntry>() {
        override fun write(out: JsonWriter, value: ReadDirectoryResultEntry) {
            out.beginArray()
            out.value(value.name)
            out.value(value.fileType.value)
            out.endArray()
        }

        override fun read(`in`: JsonReader): ReadDirectoryResultEntry {
            `in`.beginArray()
            val name = `in`.nextString()
            val fileType = FileType(`in`.nextInt())
            `in`.endArray()
            return ReadDirectoryResultEntry(name, fileType)
        }
    }
}