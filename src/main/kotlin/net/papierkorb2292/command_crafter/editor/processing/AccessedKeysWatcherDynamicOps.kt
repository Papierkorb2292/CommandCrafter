package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import java.nio.ByteBuffer
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream

class AccessedKeysWatcherDynamicOps<T>(private val delegate: DynamicOps<T>): DynamicOps<T> {
    val accessedKeys = mutableMapOf<T, MutableSet<T>>()

    private fun addAccessedKey(key: T, value: T) {
        accessedKeys.getOrPut(key) { mutableSetOf() } += value
    }

    override fun empty(): T = delegate.empty()
    override fun emptyMap(): T = delegate.emptyMap()
    override fun emptyList(): T = delegate.emptyList()
    override fun <U> convertTo(outOps: DynamicOps<U>, input: T): U = delegate.convertTo(outOps, input)
    override fun getNumberValue(input: T): DataResult<Number> = delegate.getNumberValue(input)
    override fun getNumberValue(input: T, defaultValue: Number): Number = delegate.getNumberValue(input, defaultValue)
    override fun createNumeric(i: Number): T = delegate.createNumeric(i)
    override fun createByte(value: Byte): T = delegate.createByte(value)
    override fun createShort(value: Short): T = delegate.createShort(value)
    override fun createInt(value: Int): T = delegate.createInt(value)
    override fun createLong(value: Long): T = delegate.createLong(value)
    override fun createFloat(value: Float): T = delegate.createFloat(value)
    override fun createDouble(value: Double): T = delegate.createDouble(value)
    override fun getBooleanValue(input: T): DataResult<Boolean> = delegate.getBooleanValue(input)
    override fun createBoolean(value: Boolean): T = delegate.createBoolean(value)
    override fun getStringValue(input: T): DataResult<String> = delegate.getStringValue(input)
    override fun createString(value: String): T = delegate.createString(value)
    override fun mergeToList(list: T, value: T): DataResult<T> = delegate.mergeToList(list, value)
    override fun mergeToList(list: T, values: List<T>): DataResult<T> = delegate.mergeToList(list, values)
    override fun mergeToMap(map: T, key: T, value: T): DataResult<T> = delegate.mergeToMap(map, key, value)
    override fun mergeToMap(map: T, values: Map<T, T>): DataResult<T> = delegate.mergeToMap(map, values)
    override fun mergeToMap(map: T, values: MapLike<T>): DataResult<T> = delegate.mergeToMap(map, values)
    override fun mergeToPrimitive(prefix: T, value: T): DataResult<T> = delegate.mergeToPrimitive(prefix, value)
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
    override fun createMap(map: Stream<Pair<T, T>>): T = delegate.createMap(map)
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
            }
        }
    override fun createMap(map: Map<T, T>): T = delegate.createMap(map)
    override fun getStream(input: T): DataResult<Stream<T>> = delegate.getStream(input)
    override fun getList(input: T): DataResult<Consumer<Consumer<T>>> = delegate.getList(input)
    override fun createList(input: Stream<T>): T = delegate.createList(input)
    override fun getByteBuffer(input: T): DataResult<ByteBuffer> = delegate.getByteBuffer(input)
    override fun createByteList(input: ByteBuffer): T = delegate.createByteList(input)
    override fun getIntStream(input: T): DataResult<IntStream> = delegate.getIntStream(input)
    override fun createIntList(input: IntStream): T = delegate.createIntList(input)
    override fun getLongStream(input: T): DataResult<LongStream> = delegate.getLongStream(input)
    override fun createLongList(input: LongStream): T = delegate.createLongList(input)
    override fun remove(input: T, key: String): T = delegate.remove(input, key)
    override fun compressMaps() = delegate.compressMaps()
    override fun get(input: T, key: String): DataResult<T> {
        addAccessedKey(input, delegate.createString(key))
        return delegate.get(input, key)
    }
    override fun getGeneric(input: T, key: T): DataResult<T> {
        addAccessedKey(input, key)
        return delegate.getGeneric(input, key)
    }
    override fun set(input: T, key: String, value: T): T = delegate.set(input, key, value)
    override fun update(input: T, key: String, function: Function<T, T>): T = delegate.update(input, key, function)
    override fun updateGeneric(input: T, key: T, function: Function<T, T>): T = delegate.updateGeneric(input, key, function)
    override fun listBuilder(): ListBuilder<T> = delegate.listBuilder()
    override fun mapBuilder(): RecordBuilder<T> = delegate.mapBuilder()
    override fun <E> withEncoder(encoder: Encoder<E>): Function<E, DataResult<T>> = delegate.withEncoder(encoder)
    override fun <E> withDecoder(decoder: Decoder<E>): Function<T, DataResult<Pair<E, T>>> = delegate.withDecoder(decoder)
    override fun <E> withParser(decoder: Decoder<E>): Function<T, DataResult<E>> = delegate.withParser(decoder)
    override fun <U> convertList(outOps: DynamicOps<U>, input: T): U = delegate.convertList(outOps, input)
    override fun <U> convertMap(outOps: DynamicOps<U>, input: T): U = delegate.convertMap(outOps, input)
}