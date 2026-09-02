package net.papierkorb2292.command_crafter.helper

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.netty.buffer.ByteBuf
import net.minecraft.network.VarInt
import net.minecraft.network.codec.StreamCodec
import java.util.*
import kotlin.math.max

class IntList(capacity: Int) {
    constructor() : this(10)

    companion object {
        fun intListOf(vararg content: Int) = IntList(content.size).apply {
            size = content.size
            content.copyInto(entries)
        }

        fun intListOfZeros(size: Int) = IntList(size).also { it.size = size }

        val PACKET_CODEC = object : StreamCodec<ByteBuf, IntList> {
            override fun decode(input: ByteBuf): IntList {
                val length = VarInt.read(input)
                val result = intListOfZeros(length)
                for(i in 0 until length) {
                    result[i] = VarInt.read(input)
                }
                return result
            }

            override fun encode(output: ByteBuf, value: IntList) {
                VarInt.write(output, value.size)
                for(i in 0 until value.size) {
                    VarInt.write(output, value[i])
                }
            }
        };
    }

    private var entries = IntArray(capacity)
    var size = 0
        private set

    operator fun get(index: Int): Int {
        Objects.checkIndex(index, size)
        return entries[index]
    }

    operator fun set(index: Int, element: Int) {
        Objects.checkIndex(index, size)
        entries[index] = element
    }

    operator fun plus(element: Int) = copy(size + 1).also {
        it.size++
        it[size] = element
    }
    operator fun plusAssign(element: Int) = add(element)

    fun add(element: Int) = add(size, element)

    fun add(position: Int, element: Int) {
        Objects.checkIndex(position, size + 1)
        if(size == entries.size)
            grow()
        if(position != size)
            entries.copyInto(entries, position + 1, position, size)
        entries[position] = element
        size++
    }

    operator fun plus(other: IntList) = copy(size + other.size).also {
        it.size += other.size
        other.entries.copyInto(it.entries, size, 0, other.size)
    }
    operator fun plusAssign(other: IntList) = addAll(other)

    fun addAll(other: IntList) = addAll(size, other)

    fun addAll(position: Int, other: IntList) {
        Objects.checkIndex(position, size + 1)
        if(size + other.size > entries.size)
            grow(size + other.size)
        if(position != size)
            entries.copyInto(entries, position + other.size, position, size)
        other.entries.copyInto(entries, position, 0, other.size)
        size += other.size
    }

    fun addAllSorted(other: IntList) {
        val newSize = entries.size + other.entries.size
        val newEntries = IntArray(newSize)
        var i = 0
        var j = 0
        var k = 0
        while(i < size && j < other.size)
            newEntries[k++] = if(entries[i] <= other.entries[j]) entries[i++] else other.entries[j++]
        while(i < size)
            newEntries[k++] = entries[i++]
        while(j < other.size)
            newEntries[k++] = other.entries[j++]
        entries = newEntries
        size += other.size
    }

    fun remove(position: Int): Int {
        Objects.checkIndex(position, size)
        val element = entries[position]
        if(position != size - 1)
            entries.copyInto(entries, position, position + 1, size)
        size--
        return element
    }

    fun removeAfter(start: Int) {
        if(start == -1) {
            size = 0
            return
        }
        Objects.checkIndex(start, size)
        size = start + 1
    }

    fun isEmpty() = size == 0

    fun first() = get(0)
    fun last() = get(size - 1)

    fun lastIndexOf(element: Int): Int {
        for (i in size - 1 downTo 0) {
            if (entries[i] == element) {
                return i
            }
        }
        return -1
    }

    fun indexOf(element: Int): Int {
        for (i in 0 until size) {
            if (entries[i] == element) {
                return i
            }
        }
        return -1
    }

    fun containsAll(elements: Collection<Int>): Boolean {
        for (element in elements)
            if (!contains(element))
                return false
        return true
    }

    fun clear() {
        size = 0
    }

    fun contains(element: Int) = indexOf(element) != -1

    fun any(predicate: (Int) -> Boolean): Boolean {
        for(i in 0 until size)
            if(predicate(entries[i]))
                return true
        return false
    }
    fun all(predicate: (Int) -> Boolean): Boolean {
        for(i in 0 until size)
            if(!predicate(entries[i]))
                return false
        return true
    }

    fun copy(capacity: Int = size) = IntList(capacity).also {
        it.size = size
        entries.copyInto(it.entries, 0, 0, size)
    }

    inline fun map(transform: (Int) -> Int): IntList {
        val result = intListOfZeros(size)
        for(i in 0 until size)
            result[i] = transform(this[i])
        return result
    }

    override fun equals(other: Any?): Boolean {
        if(other !is IntList)
            return false
        if(size != other.size)
            return false
        for(i in 0 until size)
            if(entries[i] != other.entries[i])
                return false
        return true
    }

    override fun hashCode(): Int {
        var result = 1
        for(i in 0 until size) {
            result = 31 * result + entries[i]
        }
        return result
    }

    override fun toString(): String {
        val builder = StringBuilder("[")
        for(i in 0 until size) {
            if(i != 0) builder.append(',')
            builder.append(entries[i])
        }
        builder.append(']')
        return builder.toString()
    }

    private fun grow(minSize: Int = 1) {
        entries = entries.copyOf(max(entries.size * 2, minSize))
    }

    object TypeAdapter : com.google.gson.TypeAdapter<IntList>() {

        override fun write(out: JsonWriter, value: IntList) {
            out.beginArray()
            for(i in 0 until value.size) {
                out.value(value[i])
            }
            out.endArray()
        }

        override fun read(`in`: JsonReader): IntList {
            val result = IntList()
            `in`.beginArray()
            while(`in`.peek() == JsonToken.NUMBER) {
                result += `in`.nextInt()
            }
            `in`.endArray()
            return result
        }
    }

    object JacksonSerializer : JsonSerializer<IntList>() {
        override fun serialize(
            value: IntList,
            gen: JsonGenerator,
            serializers: SerializerProvider,
        ) {
            gen.writeArray(value.entries, 0, value.size)
        }
    }
}