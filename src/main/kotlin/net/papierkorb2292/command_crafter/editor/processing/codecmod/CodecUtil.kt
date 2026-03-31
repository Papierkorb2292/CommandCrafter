package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.google.gson.JsonElement
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.papierkorb2292.command_crafter.codecmod.NoDecoderCallbacks
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import java.util.*

fun <T> Codec<T>.beforeDecode(callback: BeforeDecodeCallback) = object : Codec<T> {
    override fun <A> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        this@beforeDecode.encode(input, ops, prefix)

    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> {
        callback.invoke(input, ops)
        return this@beforeDecode.decode(ops, input)
    }
}

fun <T> Codec<T>.afterDecode(callback: AfterDecodeCallback<T>) = object : Codec<T> {
    override fun <A> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        this@afterDecode.encode(input, ops, prefix)

    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        this@afterDecode.decode(ops, input).map { result ->
            callback.invoke(result.first, input, ops)
            result
        }
}

fun <T> Codec<T>.withJsonEncodeAlternative(jsonEncoder: Encoder<T>) = object : Codec<T> {
    override fun <A : Any> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        if(prefix is JsonElement)
            jsonEncoder.encode(input, ops, prefix)
        else this@withJsonEncodeAlternative.encode(input, ops, prefix)

    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        this@withJsonEncodeAlternative.decode(ops, input)
}

fun <F> Decoder<F>.onlyAnalyzingBehavior() = @NoDecoderCallbacks object : Codec<F> {
    override fun <T : Any> decode(
        ops: DynamicOps<T>,
        input: T,
    ): DataResult<Pair<F, T>> {
        val behavior = ExtraDecoderBehavior.getCurrentBehavior(ops)
        return if(behavior != null && behavior.nodeAnalyzingBehavior == null)
            ExtraDecoderBehavior.decodeWithBehavior(this@onlyAnalyzingBehavior, ops, input, ExtraDecoderContext(behavior))
        else this@onlyAnalyzingBehavior.decode(ops, input)
    }

    override fun <T> encode(
        input: F,
        ops: DynamicOps<T>,
        prefix: T,
    ): DataResult<T> = DataResult.success(prefix)
}

fun <O, F : Any> Decoder<F>.onlyAnalyzingRecord(field: String): RecordCodecBuilder<O, Optional<F>> = RecordCodecBuilder.of(
    { Optional.empty() },
    this@onlyAnalyzingRecord.onlyAnalyzingBehavior().lenientOptionalFieldOf(field)
)
fun <T> MapCodec<T>.forGetterIdent(): RecordCodecBuilder<T, T> = forGetter { it }
fun <O, F : Any> MapCodec<Optional<F>>.forEmptyGetter(): RecordCodecBuilder<O, Optional<F>> = forGetter { Optional.empty() }

fun <T> Decoder<T>.decodeParent() = object : Decoder<T> {
    override fun <A : Any> decode(
        ops: DynamicOps<A>,
        input: A,
    ): DataResult<Pair<T, A>> {
        val parent = ExtraDecoderBehavior.getCurrentBehavior(ops)?.parentLinks?.getParent(input)
            ?: return DataResult.error { "Node doesn't have parent" }
        return parent.decode(this@decodeParent).map { it.mapSecond { ops.empty() } }
    }
}

fun <T, V> Decoder<T>.withThreadLocal(threadLocal: ThreadLocal<V>, value: V): Decoder<T> = object : Decoder<T> {
    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        threadLocal.runWithValueSwap(value) {
            this@withThreadLocal.decode(ops, input)
        }
}

fun <T> unitDecoder(unit: T) = object : Decoder<T> {
    override fun <A : Any> decode(ops: DynamicOps<A>, input: A) =
        DataResult.success(Pair(unit, ops.empty()))
}

interface BeforeDecodeCallback {
    fun <TNode: Any> invoke(input: TNode, ops: DynamicOps<TNode>)
}
interface AfterDecodeCallback<TResult> {
    fun <TNode: Any> invoke(result: TResult, input: TNode, ops: DynamicOps<TNode>)
}