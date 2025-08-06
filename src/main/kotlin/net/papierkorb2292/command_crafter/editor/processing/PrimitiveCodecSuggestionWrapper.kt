package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.codecs.PrimitiveCodec
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper.SuggestionsProvider
import net.papierkorb2292.command_crafter.helper.getOrNull

class PrimitiveCodecSuggestionWrapper<A>(private val delegate: PrimitiveCodec<A>, val suggestionsProvider: SuggestionsProvider): PrimitiveCodec<A> {
    override fun <T> read(
        ops: DynamicOps<T>,
        input: T,
    ): DataResult<A> {
        if(input == null) return delegate.read(ops, null)
        StringRangeTree.AnalyzingDynamicOps.CURRENT_ANALYZING_OPS.getOrNull()?.let { analyzingOps ->
            @Suppress("UNCHECKED_CAST")
            val castedOps = analyzingOps as StringRangeTree.AnalyzingDynamicOps<T>
            suggestionsProvider.getSuggestions(ops).forEach {
                castedOps.getNodeStartSuggestions(input) += StringRangeTree.Suggestion(it)
            }
        }
        return delegate.read(ops, input)
    }

    override fun <T> write(ops: DynamicOps<T>, value: A): T =
        delegate.write(ops, value)
}