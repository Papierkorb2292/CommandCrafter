package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.google.gson.JsonElement
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.papierkorb2292.command_crafter.codecmod.NoDecoderCallbacks
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

val IS_CUSTOM_ENCODE = ThreadLocal<Boolean>()

fun <T, A> Encoder<T>.customEncode(ops: DynamicOps<A>, input: T): DataResult<A> = IS_CUSTOM_ENCODE.runWithValueSwap(true) {
    encodeStart(ops, input)
}

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
        if(prefix is JsonElement && IS_CUSTOM_ENCODE.getOrNull() == true)
            jsonEncoder.encode(input, ops, prefix)
        else this@withJsonEncodeAlternative.encode(input, ops, prefix)

    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        this@withJsonEncodeAlternative.decode(ops, input)
}

fun <T> Codec<T>.nonCanonical(): Codec<T> = object : Codec<T> {
    override fun <A : Any> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        this@nonCanonical.encode(input, ops, prefix)

    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> {
        val extraBehavior = ExtraDecoderBehavior.getCurrentBehavior(ops)
        val nonCanonicalBehavior = extraBehavior?.branchBehavior?.nonCanonicalBehavior
            ?: return this@nonCanonical.decode(ops, input)
        return when(nonCanonicalBehavior) {
            ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE -> DataResult.error { "Non-canonical value not allowed here" }
            ExtraDecoderBehavior.NonCanonicalBehavior.KEEP_BRANCH_BEHAVIOR -> this@nonCanonical.decode(ops, input)
            ExtraDecoderBehavior.NonCanonicalBehavior.DROP_DOWN_TO_DECODE ->
                extraBehavior.decodeWithBehavior(BranchBehaviorProvider.DROP_TO_DECODE_BEHAVIOR_MODIFIER, false) {
                    this@nonCanonical.decode(ops, input)
                }
        }
    }
}

/*fun <T> Codec<T>.decodeAsWarnings(replacement: T, downgradeCondition: (Dynamic<*>) -> Boolean = { true }): Codec<T> = object : Codec<T> {
    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> {
        val extraBehavior = ExtraDecoderBehavior.getCurrentBehavior(ops)
        if(extraBehavior == null || !downgradeCondition(Dynamic(extraBehavior.onlyContextOps, input)))
            return this@decodeAsWarnings.decode(ops, input)
        return DataResult.success(extraBehavior.decodeWithBehavior(BranchBehaviorProvider.DEFAULT_BEHAVIOR_MODIFIER, true) {
            this@decodeAsWarnings.decode(ops, input)
        }.result().orElse(Pair.of(replacement, ops.empty())))
    }

    override fun <A : Any> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        this@decodeAsWarnings.encode(input, ops, prefix)
}*/

// Conditionally downgrades all errors from the codec to warnings. A replacement can be given, in case the result is passed to another codec, and it's not supposed to be marked as another error
fun <T> MapCodec<T>.decodeAsWarnings(replacement: T? = null, downgradeCondition: MapDecoder<Boolean> = MapCodec.unit(true)): MapCodec<T> = object : MapCodec<T>() {
    override fun <A> keys(ops: DynamicOps<A>) = this@decodeAsWarnings.keys(ops)
    override fun <A : Any> decode(
        ops: DynamicOps<A>,
        input: MapLike<A>,
    ): DataResult<T> {
        val extraBehavior = ExtraDecoderBehavior.getCurrentBehavior(ops) ?: return this@decodeAsWarnings.decode(ops, input)
        val shouldDowngrade = downgradeCondition.decode(extraBehavior.onlyContextOps, input).result().getOrNull() ?: false
        if(!shouldDowngrade)
            return this@decodeAsWarnings.decode(ops, input)
        val originalResult = extraBehavior.decodeWithBehavior(BranchBehaviorProvider.DEFAULT_BEHAVIOR_MODIFIER, true) {
            this@decodeAsWarnings.decode(ops, input)
        }
        if(replacement == null)
            return originalResult
        return DataResult.success(originalResult.result().orElseGet { replacement })
    }

    override fun <A> encode(
        input: T,
        ops: DynamicOps<A>,
        prefix: RecordBuilder<A>,
    ): RecordBuilder<A> = this@decodeAsWarnings.encode(input, ops, prefix)
}

fun <T> Codec<T>.withMacroCheck(): Codec<MacroChecked<T>> = object : Codec<MacroChecked<T>> {
    override fun <A : Any> encode(input: MacroChecked<T>, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        input.result.flatMap { this@withMacroCheck.encode(it, ops, prefix) }

    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<MacroChecked<T>, A>> {
        val result = this@withMacroCheck.decode(ops, input)
        val extraBehavior = ExtraDecoderBehavior.getCurrentBehavior(ops)
        return DataResult.success(Pair.of(
            MacroChecked(result.map { it.first }, extraBehavior?.hasMacro(input) ?: false),
            result.result().getOrNull()?.second ?: ops.empty())
        )
    }
}

data class MacroChecked<T>(val result: DataResult<T>, val hasMacro: Boolean)

fun <T> Codec<T>.markEncodedId(fieldName: String): Codec<T> = object : Codec<T> {
    override fun <A : Any> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        this@markEncodedId.encode(input, ops, prefix)

    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> {
        val nonCanonicalBehavior = ExtraDecoderBehavior.getCurrentBehavior(ops)?.branchBehavior?.nonCanonicalBehavior
        if(nonCanonicalBehavior == ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE) {
            val id = ops.get(input, fieldName).result().getOrNull()
            return CodecTransformers.EXTRA_CANONICAL_ID.runWithValueSwap(id) {
                this@markEncodedId.decode(ops, input)
            }
        }
        return this@markEncodedId.decode(ops, input)
    }

}

fun <F> Decoder<F>.onlyContextBehavior() = @NoDecoderCallbacks object : Decoder<F> {
    override fun <T : Any> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<F, T>> {
        val behavior = ExtraDecoderBehavior.getCurrentBehavior(ops) ?: return this@onlyContextBehavior.decode(ops, input)
        return ExtraDecoderBehavior.decodeWithBehavior(this@onlyContextBehavior, ops, input, ExtraDecoderContext(behavior))
    }
}

fun <F> Decoder<F>.onlyAnalyzingBehavior() = @NoDecoderCallbacks object : Codec<F> {
    override fun <T : Any> decode(
        ops: DynamicOps<T>,
        input: T,
    ): DataResult<Pair<F, T>> {
        val behavior = ExtraDecoderBehavior.getCurrentBehavior(ops)
        return if(behavior != null && behavior.nodeAnalyzingTracker == null)
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

fun <T> Decoder<T>.decodeParent(levels: Int = 1) = object : Codec<T> {
    override fun <A : Any> decode(
        ops: DynamicOps<A>,
        input: A,
    ): DataResult<Pair<T, A>> {
        val parentLinks = ExtraDecoderBehavior.getCurrentBehavior(ops)?.parentLinks
        var current: Dynamic<*> = Dynamic(ops, input)
        repeat(levels) {
            current = parentLinks?.getParent(current.value)
                ?: return DataResult.error { "Node doesn't have parent" }
        }
        return current.decode(this@decodeParent).map { it.mapSecond { input } } // Returns input as second pair entry, so the codec can be chained with Codec.pair
    }

    override fun <A> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> = DataResult.success(prefix)
}

fun <T, V> Decoder<T>.withThreadLocal(threadLocal: ThreadLocal<V>, value: V): Decoder<T> = object : Decoder<T> {
    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        threadLocal.runWithValueSwap(value) {
            this@withThreadLocal.decode(ops, input)
        }
}

fun <T, V> Decoder<T>.withConditionalThreadLocal(threadLocal: ThreadLocal<V>, value: V, condition: () -> Boolean): Decoder<T> = object : Decoder<T> {
    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        if(condition()) threadLocal.runWithValueSwap(value) {
            this@withConditionalThreadLocal.decode(ops, input)
        } else this@withConditionalThreadLocal.decode(ops, input)
}

fun <T> unitDecoder(unit: T) = object : Decoder<T> {
    override fun <A : Any> decode(ops: DynamicOps<A>, input: A) =
        DataResult.success(Pair(unit, ops.empty()))
}

fun <T, K> Decoder<T>.encodedFieldOf(key: K, keyDecoder: Decoder<K>) = object : Decoder<T> {
    override fun <A : Any> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        ops.getMap(input).flatMap { map ->
            map.entries().filter {
                keyDecoder.decode(ops, it.first).result().getOrNull()?.first == key
            }.findFirst()
                .map { this@encodedFieldOf.decode(ops, it.second) }
                .getOrNull()
                ?: DataResult.error { "No encoded key $key in $input" }
        }
}

fun <T> Codec<T>.conditionalDecode(condition: () -> Boolean, otherwise: Decoder<T>) = object : Codec<T> {
    override fun <A> encode(input: T, ops: DynamicOps<A>, prefix: A): DataResult<A> =
        this@conditionalDecode.encode(input, ops, prefix)

    override fun <A> decode(ops: DynamicOps<A>, input: A): DataResult<Pair<T, A>> =
        if(condition()) this@conditionalDecode.decode(ops, input) else otherwise.decode(ops, input)
}

fun <T> MapCodec<T>.conditionalDecode(condition: () -> Boolean, otherwise: MapDecoder<T>) = object : MapCodec<T>() {
    override fun <A> keys(ops: DynamicOps<A>): Stream<A> = this@conditionalDecode.keys(ops)

    override fun <A> encode(input: T, ops: DynamicOps<A>, prefix: RecordBuilder<A>): RecordBuilder<A> = this@conditionalDecode.encode(input, ops, prefix)

    override fun <A> decode(ops: DynamicOps<A>, input: MapLike<A>): DataResult<T> =
        if(condition()) this@conditionalDecode.decode(ops, input) else otherwise.decode(ops, input)
}

interface BeforeDecodeCallback {
    fun <TNode: Any> invoke(input: TNode, ops: DynamicOps<TNode>)
}
interface AfterDecodeCallback<TResult> {
    fun <TNode: Any> invoke(result: TResult, input: TNode, ops: DynamicOps<TNode>)
}