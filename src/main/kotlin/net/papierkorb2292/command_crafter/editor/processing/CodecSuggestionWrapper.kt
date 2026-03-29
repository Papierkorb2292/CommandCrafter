package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.helper.getOrNull
import java.util.stream.Stream

class CodecSuggestionWrapper<A, TContext>(private val delegate: Codec<A>, val contextGetter: (ExtraDecoderBehavior<*>) -> TContext, val suggestionsProvider: ContextSuggestionsProvider<TContext>): Codec<A> {
    companion object {
        fun <A> simple(delegate: Codec<A>, suggestionProvider: SuggestionsProvider): Codec<A> =
            CodecSuggestionWrapper(delegate, { }, object : ContextSuggestionsProvider<Unit> {
                override fun <T : Any> getSuggestions(ops: DynamicOps<T>, context: Unit): Stream<T> = suggestionProvider.getSuggestions(ops)
                override fun <T : Any> suggestionModifier(suggestion: ExtraDecoderBehavior.PossibleValue<T>, ops: DynamicOps<T>): ExtraDecoderBehavior.PossibleValue<T> = suggestionProvider.suggestionModifier(suggestion, ops)
            })

        fun <A, TContext> withThreadLocal(delegate: Codec<A>, threadLocal: ThreadLocal<TContext>, suggestionProvider: ContextSuggestionsProvider<TContext?>): Codec<A> =
            CodecSuggestionWrapper(delegate, { threadLocal.getOrNull() }, suggestionProvider)
    }

    override fun <T> encode(input: A, ops: DynamicOps<T>, prefix: T): DataResult<T>
        = delegate.encode(input, ops, prefix)

    override fun <T: Any> decode(ops: DynamicOps<T>, input: T?): DataResult<Pair<A, T>> {
        if(input == null) return delegate.decode(ops, null)
        ExtraDecoderBehavior.getCurrentBehavior(ops)?.let { extraBehavior ->
            val context = contextGetter(extraBehavior)
            extraBehavior.notePossibleValues(input,{
                suggestionsProvider.getSuggestions(ops, context).map {
                    suggestionsProvider.suggestionModifier(ExtraDecoderBehavior.PossibleValue(it), ops)
                }
            })
        }
        return delegate.decode(ops, input)
    }

    interface SuggestionsProvider {
        fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T>
        fun <T: Any> suggestionModifier(suggestion: ExtraDecoderBehavior.PossibleValue<T>, ops: DynamicOps<T>): ExtraDecoderBehavior.PossibleValue<T> = suggestion
    }

    interface ContextSuggestionsProvider<TContext> {
        fun <T: Any> getSuggestions(ops: DynamicOps<T>, context: TContext): Stream<T>
        fun <T: Any> suggestionModifier(suggestion: ExtraDecoderBehavior.PossibleValue<T>, ops: DynamicOps<T>): ExtraDecoderBehavior.PossibleValue<T> = suggestion
    }
}