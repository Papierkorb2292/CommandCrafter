package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import com.mojang.serialization.codecs.RecordCodecBuilder
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

fun <O, F : Any> Codec<F>.onlyAnalyzingRecord(field: String): RecordCodecBuilder<O, Optional<F>> = RecordCodecBuilder.of(
    { Optional.empty() },
    Codec.of(MapCodec.unit<F> { null }.codec(), object : Decoder<F> {
        override fun <T : Any> decode(
            ops: DynamicOps<T>,
            input: T,
        ): DataResult<Pair<F, T>> {
            val result = this@onlyAnalyzingRecord.decode(ops, input)
            ExtraDecoderBehavior.getCurrentBehavior(ops)?.commitErrors(ExtraDecoderBehavior.DecoderErrorLevel.IGNORE)
            return result
        }
    }).lenientOptionalFieldOf(field)
)
fun <T> MapCodec<T>.forGetterIdent(): RecordCodecBuilder<T, T> = forGetter { it }

interface BeforeDecodeCallback {
    fun <TNode: Any> invoke(input: TNode, ops: DynamicOps<TNode>)
}
interface AfterDecodeCallback<TResult> {
    fun <TNode: Any> invoke(result: TResult, input: TNode, ops: DynamicOps<TNode>)
}