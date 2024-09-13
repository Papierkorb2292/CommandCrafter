package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class ReadDirectoryResultEntry(
    var name: String,
    var fileType: FileType
) {

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