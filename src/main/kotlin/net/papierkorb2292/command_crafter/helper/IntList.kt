package net.papierkorb2292.command_crafter.helper

import java.util.*
import kotlin.math.max

class IntList(capacity: Int) {
    constructor() : this(10)

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

    fun contains(element: Int) = indexOf(element) != -1

    fun copy() = IntList().also { it.addAll(this) }

    private fun grow(minSize: Int = 1) {
        entries = entries.copyOf(max(entries.size * 2, minSize))
    }
}