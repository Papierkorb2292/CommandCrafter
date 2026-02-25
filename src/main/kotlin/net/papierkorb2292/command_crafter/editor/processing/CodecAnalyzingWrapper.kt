package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.getOrNull
import kotlin.jvm.optionals.getOrNull

class CodecAnalyzingWrapper<A>(private val delegate: Codec<A>, val callback: (AnalyzingResult, StringRange, A, DynamicOps<*>) -> Unit): Codec<A> {
    override fun <T> encode(input: A, ops: DynamicOps<T>, prefix: T): DataResult<T>
            = delegate.encode(input, ops, prefix)

    override fun <T: Any> decode(ops: DynamicOps<T>, input: T?): DataResult<Pair<A, T>> {
        val result = delegate.decode(ops, input)
        val decoded = result.result().getOrNull()
        if(input == null || decoded == null) return result
        StringRangeTree.AnalyzingDynamicOps.CURRENT_ANALYZING_OPS.getOrNull()?.let { analyzingOps ->
            @Suppress("UNCHECKED_CAST")
            val castedOps = analyzingOps as StringRangeTree.AnalyzingDynamicOps<T>
            val range = analyzingOps.tree.ranges[input]!!
            val analyzingResult = castedOps.createNodeAnalyzingResultOverlay(input)
            callback(analyzingResult, range, decoded.first, ops)
            castedOps.finishNodeAnalyzingResultOverlay(input, analyzingResult)
        }
        return result
    }
}