package net.papierkorb2292.command_crafter.helper

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import jdk.internal.util.ArraysSupport
import java.util.*
import kotlin.math.max

class IntList(capacity: Int) {
    constructor() : this(10)

    companion object {
        fun intListOf(vararg content: Int) = IntList(content.size).apply {
            size = content.size
            content.copyInto(entries)
        }
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

    operator fun plus(element: Int) = copy().apply { this += element }
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

    operator fun plus(other: IntList) = copy().apply { this += other }
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

    fun remove(position: Int): Int {
        Objects.checkIndex(position, size)
        val element = entries[position]
        if(position != size - 1)
            entries.copyInto(entries, position, position + 1, size)
        size--
        return element
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

    fun copy() = IntList().also { it.addAll(this) }

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
}