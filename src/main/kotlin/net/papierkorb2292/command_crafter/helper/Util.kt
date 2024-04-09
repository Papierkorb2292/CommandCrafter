package net.papierkorb2292.command_crafter.helper

import org.apache.commons.compress.harmony.pack200.IntList
import java.util.concurrent.Semaphore

fun IntList.binarySearch(fromIndex: Int = 0, toIndex: Int = size(), comparison: (index: Int) -> Int): Int {
    var low = fromIndex
    var high = toIndex - 1

    while (low <= high) {
        val mid = (low + high).ushr(1)
        val cmp = comparison(mid)

        when {
            cmp < 0 -> low = mid + 1
            cmp > 0 -> high = mid - 1
            else -> return mid
        }
    }

    return -(low + 1)
}

inline fun <reified T> arrayOfNotNull(vararg elements: T?): Array<T> {
    var index = 0
    return Array(elements.count { it != null }) {
        while (elements[index] == null) index++
        elements[index++]!!
    }
}

inline fun <T> Semaphore.withAcquired(block: () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}