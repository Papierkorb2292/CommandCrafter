package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import net.papierkorb2292.command_crafter.helper.getOrNull
import java.util.stream.Stream

class CodecSuggestionWrapper<A>(private val delegate: Codec<A>, val suggestionsProvider: SuggestionsProvider): Codec<A> {
    override fun <T> encode(input: A, ops: DynamicOps<T>, prefix: T): DataResult<T>
        = delegate.encode(input, ops, prefix)

    override fun <T: Any> decode(ops: DynamicOps<T>, input: T?): DataResult<Pair<A, T>> {
        if(input == null) return delegate.decode(ops, null)
        StringRangeTree.AnalyzingDynamicOps.CURRENT_ANALYZING_OPS.getOrNull()?.let { analyzingOps ->
            @Suppress("UNCHECKED_CAST")
            val castedOps = analyzingOps as StringRangeTree.AnalyzingDynamicOps<T>
            suggestionsProvider.getSuggestions(ops).forEach {
                castedOps.getNodeStartSuggestions(input) += StringRangeTree.Suggestion(it)
            }
        }
        return delegate.decode(ops, input)
    }

    interface SuggestionsProvider {
        fun <T> getSuggestions(ops: DynamicOps<T>): Stream<T>
    }
}