package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import java.nio.ByteBuffer
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream

class AccessedKeysWatcherDynamicOps<T>(override val delegate: DynamicOps<T>): DelegatingDynamicOps<T> {
    val accessedKeys = mutableMapOf<T, MutableSet<T>>()

    private fun addAccessedKey(map: T, key: T) {
        accessedKeys.getOrPut(map) { Collections.newSetFromMap(IdentityHashMap()) } += key
    }

    override fun getMapValues(input: T): DataResult<Stream<Pair<T, T>>> =
        delegate.getMapValues(input).map { entryStream ->
            entryStream.map {
                addAccessedKey(input, it.first)
                it
            }
        }
    override fun getMapEntries(input: T): DataResult<Consumer<BiConsumer<T, T>>> =
        delegate.getMapEntries(input).map { entryConsumer ->
            Consumer { biConsumer ->
                entryConsumer.accept { key, value ->
                    addAccessedKey(input, key)
                    biConsumer.accept(key, value)
                }
            }
        }
    override fun getMap(input: T): DataResult<MapLike<T>> =
        delegate.getMap(input).map { delegateMap ->
            object : MapLike<T> {
                override fun get(key: T): T? {
                    addAccessedKey(input, key)
                    return delegateMap.get(key)
                }
                override fun get(key: String?): T? {
                    addAccessedKey(input, delegate.createString(key))
                    return delegateMap.get(key)
                }

                override fun entries(): Stream<Pair<T, T>> {
                    return delegateMap.entries().map {
                        addAccessedKey(input, it.first)
                        it
                    }
                }

                override fun toString(): String {
                    return delegateMap.toString()
                }
            }
        }
    override fun get(input: T, key: String): DataResult<T> {
        addAccessedKey(input, delegate.createString(key))
        return delegate.get(input, key)
    }
    override fun getGeneric(input: T, key: T): DataResult<T> {
        addAccessedKey(input, key)
        return delegate.getGeneric(input, key)
    }
}