package net.papierkorb2292.command_crafter.helper

import com.google.gson.reflect.TypeToken
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree.AnalyzingDynamicOps
import java.lang.reflect.Type
import java.util.*
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
fun <T> MutableCollection<T>?.appendNullable(other: MutableCollection<T>?): MutableCollection<T>? = when {
    this == null -> other?.toMutableList()
    other == null -> this
    else -> this.toMutableList().apply { addAll(other) }
}

/**
 * Converts the Identifier to a string while omitting the namespace if it is the default "minecraft".
 */
fun Identifier.toShortString(): String = if(namespace == "minecraft") path else toString()

fun <A> Codec<A>.orEmpty(defaultValue: A): Codec<A> = object : Codec<A> {
    override fun <T> encode(input: A, ops: DynamicOps<T>, prefix: T): DataResult<T> {
        return if(input == defaultValue) DataResult.success(prefix) else this@orEmpty.encode(input, ops, prefix)
    }

    override fun <T> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<A, T>> {
        if(input == ops.empty()) {
            // Add suggestions from other codec
            if(AnalyzingDynamicOps.CURRENT_ANALYZING_OPS.getOrNull() != null)
                this@orEmpty.decode(ops, input)

            return DataResult.success(Pair.of(defaultValue, ops.emptyList()))
        }
        return this@orEmpty.decode(ops, input)
    }
}

fun <T: Any> Optional<Optional<T>>.flatten(): Optional<T> =
    if(isEmpty) Optional.empty() else get()
fun <TParent: Any, TChild : TParent> Optional<TChild>.cast(): Optional<TParent> =
    Optional.ofNullable(orElse(null))