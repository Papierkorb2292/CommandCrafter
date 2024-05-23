package net.papierkorb2292.command_crafter.helper

class BufferedIterator<T>(val iterator: Iterator<T>) : Iterator<T> {
    private var buffer: T? = null

    override fun hasNext() = buffer != null || iterator.hasNext()

    override fun next(): T {
        buffer?.let {
            buffer = null
            return it
        }
        return iterator.next()
    }

    fun peek() = buffer ?: iterator.next().apply { buffer = this }
}