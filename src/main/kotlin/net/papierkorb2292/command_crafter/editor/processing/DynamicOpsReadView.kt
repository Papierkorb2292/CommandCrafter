package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import net.minecraft.core.HolderLookup
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueInput.ValueInputList
import net.minecraft.world.level.storage.ValueInput.TypedInputList
import net.papierkorb2292.command_crafter.helper.cast
import net.papierkorb2292.command_crafter.helper.flatten
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrDefault

// Uses dummy Encoders in a bunch of places to use methods of Codec that are not available for just a Decoder.
// Btw. don't really know why Mojang didn't use DynamicOps in the first place
class DynamicOpsReadView<TNode : Any>(val dynamic: Dynamic<TNode>, private val registries: HolderLookup.Provider, val map: Optional<MapLike<TNode>>) :
    ValueInput {
    companion object {
        fun <TNode : Any> create(
            dynamic: Dynamic<TNode>,
            registries: HolderLookup.Provider
        ): DataResult<DynamicOpsReadView<TNode>> =
            dynamic.ops.getMap(dynamic.value).map { DynamicOpsReadView(dynamic, registries, Optional.of(it)) }

        private fun <T : Any> dummyEncodeDynamicOpsReadView(readView: DynamicOpsReadView<T>) = DataResult.success(Dynamic(readView.dynamic.ops, readView.dynamic.ops.empty()))

        fun getReadViewCodec(registries: HolderLookup.Provider): Codec<DynamicOpsReadView<*>> = Codec.PASSTHROUGH.flatXmap({ create(it, registries) }, { dummyEncodeDynamicOpsReadView(it) })
        fun getListReadViewCodec(registries: HolderLookup.Provider): Codec<DynamicOpsListReadView> =
            getReadViewCodec(registries).listOf().xmap({ DynamicOpsListReadView(it.toMutableList()) }, { listOf() })
        fun <TEntry: Any> getTypedListReadViewCodec(entryCodec: Codec<TEntry>): Codec<DynamicOpsTypedListReadView<TEntry>> =
            entryCodec.listOf().xmap({ DynamicOpsTypedListReadView(it.toMutableList()) }, { listOf() })

        fun getReadDecoder(registries: HolderLookup.Provider, reader: (DynamicOpsReadView<*>) -> Unit): Decoder<Unit> {
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
    override fun <T: Any> read(mapCodec: MapCodec<T>) =
        map.flatMap { mapCodec.decode(dynamic.ops, it).resultOrPartial() }

    override fun child(key: String): Optional<ValueInput> =
        read(key, getReadViewCodec(registries)).cast()


    override fun childOrEmpty(key: String): ValueInput =
        child(key).getOrDefault(DynamicOpsReadView(Dynamic(dynamic.ops), registries, Optional.empty()))

    override fun childrenList(key: String): Optional<ValueInputList> =
        read(key, getListReadViewCodec(registries)).cast()

    override fun childrenListOrEmpty(key: String): ValueInputList =
        childrenList(key).getOrDefault(DynamicOpsListReadView(mutableListOf()))

    override fun <T: Any> list(
        key: String,
        typeCodec: Codec<T>,
    ): Optional<TypedInputList<T>> =
        read(key, getTypedListReadViewCodec(typeCodec)).cast()

    override fun <T: Any> listOrEmpty(key: String, typeCodec: Codec<T>): TypedInputList<T> =
        list(key, typeCodec).getOrDefault(DynamicOpsTypedListReadView(mutableListOf()))

    override fun getBooleanOr(key: String, fallback: Boolean): Boolean =
        read(key, Codec.BOOL).getOrDefault(fallback)

    override fun getByteOr(key: String, fallback: Byte): Byte =
        read(key, Codec.BYTE).getOrDefault(fallback)

    override fun getShortOr(key: String, fallback: Short): Int =
        read(key, Codec.SHORT).getOrDefault(fallback).toInt()

    override fun getInt(key: String): Optional<Int> =
        read(key, Codec.INT)

    override fun getIntOr(key: String, fallback: Int): Int =
        read(key, Codec.INT).getOrDefault(fallback)

    override fun getLongOr(key: String, fallback: Long): Long =
        read(key, Codec.LONG).getOrDefault(fallback)

    override fun getLong(key: String): Optional<Long> =
        read(key, Codec.LONG)

    override fun getFloatOr(key: String, fallback: Float): Float =
        read(key, Codec.FLOAT).getOrDefault(fallback)

    override fun getDoubleOr(key: String, fallback: Double): Double =
        read(key, Codec.DOUBLE).getOrDefault(fallback)

    override fun getString(key: String): Optional<String> =
        read(key, Codec.STRING)

    override fun getStringOr(key: String, fallback: String): String =
        read(key, Codec.STRING).getOrDefault(fallback)

    override fun getIntArray(key: String): Optional<IntArray> =
        read(key, Codec.INT_STREAM).map { it.toArray() }

    @Deprecated("Deprecated in Java")
    override fun lookup() = registries

    class DynamicOpsListReadView(val content: MutableList<ValueInput>) : ValueInputList {
        override fun iterator() = content.iterator()
        override fun isEmpty() = content.isEmpty()
        override fun stream() = content.stream()
    }

    class DynamicOpsTypedListReadView<T: Any>(val content: MutableList<T>) : TypedInputList<T> {
        override fun iterator() = content.iterator()
        override fun isEmpty() = content.isEmpty()
        override fun stream() = content.stream()
    }
}