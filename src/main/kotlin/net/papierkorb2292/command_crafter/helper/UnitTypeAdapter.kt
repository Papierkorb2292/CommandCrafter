package net.papierkorb2292.command_crafter.helper

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

object UnitTypeAdapter : TypeAdapter<Unit>() {
    override fun write(out: JsonWriter, value: Unit) {
        out.beginObject()
        out.endObject()
    }

    override fun read(`in`: JsonReader) {
        `in`.beginObject()
        `in`.endObject()
    }
}