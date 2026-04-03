package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.codecs.PrimitiveCodec
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper.SuggestionsProvider
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior

class PrimitiveCodecSuggestionWrapper<A>(private val delegate: PrimitiveCodec<A>, val suggestionsProvider: SuggestionsProvider): PrimitiveCodec<A> {
    override fun <T: Any> read(
        ops: DynamicOps<T>,
        input: T?,
    ): DataResult<A> {
        if(input == null) return delegate.read(ops, null)
        val extraBehavior = ExtraDecoderBehavior.getCurrentBehavior(ops) ?: return delegate.read(ops, input)
        extraBehavior.notePossibleValues(input, {
            suggestionsProvider.getSuggestions(ops).map {
                suggestionsProvider.suggestionModifier(ExtraDecoderBehavior.PossibleValue(it), ops)
            }
        })
        return extraBehavior.decodeWithoutStringSuggestion {
            delegate.read(ops, input)
        }
    }

    override fun <T> write(ops: DynamicOps<T>, value: A): T =
        delegate.write(ops, value)
}