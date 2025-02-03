package net.papierkorb2292.command_crafter.helper

import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.concurrent.Semaphore

fun IntList.binarySearch(fromIndex: Int = 0, toIndex: Int = size, comparison: (index: Int) -> Int): Int {
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

fun <T> ThreadLocal<T>.getOrNull(): T? =
    get().also { if(it == null) remove() }

inline fun <TValue, TResult> ThreadLocal<TValue>.runWithValue(value: TValue, block: () -> TResult): TResult {
    set(value)
    try {
        return block()
    } finally {
        remove()
    }
}

fun <R> (() -> R).memoize(): () -> R = lazy(this)::value

fun <P, R> ((P) -> R).memoizeLast() = object : (P) -> R {
    private var initialized = false
    private var lastParam: P? = null
    private var lastResult: R? = null

    override fun invoke(p: P): R {
        if (p != lastParam || !initialized) {
            // Call delegate function first, in case it throws an exception
            lastResult = this@memoizeLast(p)
            lastParam = p
            initialized = true
        }
        @Suppress("UNCHECKED_CAST")
        return lastResult as R
    }

    override fun toString() = "MemoizeLastFun(delegate=${this@memoizeLast}, initialized=$initialized, lastParam=$lastParam, lastResult=$lastResult)"
}

fun <P1, P2, R> ((P1, P2) -> R).memoizeLast() = object : (P1, P2) -> R {
    private var initialized = false
    private var lastParam1: P1? = null
    private var lastParam2: P2? = null
    private var lastResult: R? = null

    override fun invoke(p1: P1, p2: P2): R {
        if (p1 != lastParam1 || p2 != lastParam2 || !initialized) {
            // Call delegate function first, in case it throws an exception
            lastResult = this@memoizeLast(p1, p2)
            lastParam1 = p1
            lastParam2 = p2
            initialized = true
        }
        @Suppress("UNCHECKED_CAST")
        return lastResult as R
    }
}

inline fun <reified T> getType(): Type = object : TypeToken<T>() {}.type

fun <T> Collection<T>?.concatNullable(other: Collection<T>?): Collection<T>? = when {
    this == null -> other
    other == null -> this
    else -> this + other
}