package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import net.minecraft.registry.RegistryWrapper
import net.minecraft.storage.ReadView
import net.minecraft.storage.ReadView.ListReadView
import net.minecraft.storage.ReadView.TypedListReadView
import net.papierkorb2292.command_crafter.helper.cast
import net.papierkorb2292.command_crafter.helper.flatten
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrDefault

// Uses dummy Encoders in a bunch of places to use methods of Codec that are not available for just a Decoder.
// Btw. don't really know why Mojang didn't use DynamicOps in the first place
class DynamicOpsReadView<TNode : Any>(val dynamic: Dynamic<TNode>, private val registries: RegistryWrapper.WrapperLookup, val map: Optional<MapLike<TNode>>) : ReadView {
    companion object {
        fun <TNode : Any> create(
            dynamic: Dynamic<TNode>,
            registries: RegistryWrapper.WrapperLookup
        ): DataResult<DynamicOpsReadView<TNode>> =
            dynamic.ops.getMap(dynamic.value).map { DynamicOpsReadView(dynamic, registries, Optional.of(it)) }

        private fun <T : Any> dummyEncodeDynamicOpsReadView(readView: DynamicOpsReadView<T>) = DataResult.success(Dynamic(readView.dynamic.ops, readView.dynamic.ops.empty()))

        fun getReadViewCodec(registries: RegistryWrapper.WrapperLookup): Codec<DynamicOpsReadView<*>> = Codec.PASSTHROUGH.flatXmap({ create(it, registries) }, { dummyEncodeDynamicOpsReadView(it) })
        fun getListReadViewCodec(registries: RegistryWrapper.WrapperLookup): Codec<DynamicOpsListReadView> =
            getReadViewCodec(registries).listOf().xmap({ DynamicOpsListReadView(it.toMutableList()) }, { listOf() })
        fun <TEntry> getTypedListReadViewCodec(entryCodec: Codec<TEntry>): Codec<DynamicOpsTypedListReadView<TEntry>> =
            entryCodec.listOf().xmap({ DynamicOpsTypedListReadView(it.toMutableList()) }, { listOf() })

        fun getReadDecoder(registries: RegistryWrapper.WrapperLookup, reader: (DynamicOpsReadView<*>) -> Unit): Decoder<Unit> {
            return object : Decoder<Unit> {
                override fun <T : Any> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<Unit, T>> {
                    val dynamicOpsReadView = create(Dynamic(ops, input), registries)
                    if(dynamicOpsReadView.isError)
                        return dynamicOpsReadView.map { Pair.of(Unit, ops.createList(Stream.of(input))) }
                    reader(dynamicOpsReadView.result().get())
                    // Clear result callback to prevent it from clearing errors just because of the successful return
                    PreLaunchDecoderOutputTracker.clearResultCallback()
                    return DataResult.success(Pair.of(Unit, ops.emptyList()))
                }
            }
        }
    }

    override fun <T: Any> read(key: String, codec: Codec<T>) =
        read(codec.optionalFieldOf(key)).flatten()

    @Deprecated("Deprecated in Java")
    override fun <T> read(mapCodec: MapCodec<T>) =
        map.flatMap { mapCodec.decode(dynamic.ops, it).resultOrPartial() }

    override fun getOptionalReadView(key: String): Optional<ReadView> =
        read(key, getReadViewCodec(registries)).cast()


    override fun getReadView(key: String): ReadView =
        getOptionalReadView(key).getOrDefault(DynamicOpsReadView(Dynamic(dynamic.ops), registries, Optional.empty()))

    override fun getOptionalListReadView(key: String): Optional<ListReadView> =
        read(key, getListReadViewCodec(registries)).cast()

    override fun getListReadView(key: String): ListReadView =
        getOptionalListReadView(key).getOrDefault(DynamicOpsListReadView(mutableListOf()))

    override fun <T> getOptionalTypedListView(
        key: String,
        typeCodec: Codec<T>,
    ): Optional<TypedListReadView<T>> =
        read(key, getTypedListReadViewCodec(typeCodec)).cast()

    override fun <T> getTypedListView(key: String, typeCodec: Codec<T>): TypedListReadView<T> =
        getOptionalTypedListView(key, typeCodec).getOrDefault(DynamicOpsTypedListReadView(mutableListOf()))

    override fun getBoolean(key: String, fallback: Boolean): Boolean =
        read(key, Codec.BOOL).getOrDefault(fallback)

    override fun getByte(key: String, fallback: Byte): Byte =
        read(key, Codec.BYTE).getOrDefault(fallback)

    override fun getShort(key: String, fallback: Short): Int =
        read(key, Codec.SHORT).getOrDefault(fallback).toInt()

    override fun getOptionalInt(key: String): Optional<Int> =
        read(key, Codec.INT)

    override fun getInt(key: String, fallback: Int): Int =
        read(key, Codec.INT).getOrDefault(fallback)

    override fun getLong(key: String, fallback: Long): Long =
        read(key, Codec.LONG).getOrDefault(fallback)

    override fun getOptionalLong(key: String): Optional<Long> =
        read(key, Codec.LONG)

    override fun getFloat(key: String, fallback: Float): Float =
        read(key, Codec.FLOAT).getOrDefault(fallback)

    override fun getDouble(key: String, fallback: Double): Double =
        read(key, Codec.DOUBLE).getOrDefault(fallback)

    override fun getOptionalString(key: String): Optional<String> =
        read(key, Codec.STRING)

    override fun getString(key: String, fallback: String): String =
        read(key, Codec.STRING).getOrDefault(fallback)

    override fun getOptionalIntArray(key: String): Optional<IntArray> =
        read(key, Codec.INT_STREAM).map { it.toArray() }

    @Deprecated("Deprecated in Java")
    override fun getRegistries() = registries

    class DynamicOpsListReadView(val content: MutableList<ReadView>) : ListReadView {
        override fun iterator() = content.iterator()
        override fun isEmpty() = content.isEmpty()
        override fun stream() = content.stream()
    }

    class DynamicOpsTypedListReadView<T>(val content: MutableList<T>) : TypedListReadView<T> {
        override fun iterator() = content.iterator()
        override fun isEmpty() = content.isEmpty()
        override fun stream() = content.stream()
    }
}