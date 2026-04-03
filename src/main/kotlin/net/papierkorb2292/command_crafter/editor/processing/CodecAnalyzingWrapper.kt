package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import kotlin.jvm.optionals.getOrNull

class CodecAnalyzingWrapper<A>(private val delegate: Codec<A>, val callback: (AnalyzingResult, StringRange, A, DynamicOps<*>) -> Unit): Codec<A> {
    override fun <T> encode(input: A, ops: DynamicOps<T>, prefix: T): DataResult<T>
            = delegate.encode(input, ops, prefix)

    override fun <T: Any> decode(ops: DynamicOps<T>, input: T?): DataResult<Pair<A, T>> {
        val result = delegate.decode(ops, input)
        val decoded = result.result().getOrNull()
        if(input == null || decoded == null) return result
        ExtraDecoderBehavior.getCurrentBehavior(ops)?.nodeAnalyzingTracker?.registerCallback(input) { analyzingBehavior ->
            @Suppress("UNCHECKED_CAST")
            val analyzingResult = analyzingBehavior.createNodeAnalyzingResultOverlay()
            callback(analyzingResult, analyzingBehavior.range, decoded.first, ops)
            analyzingBehavior.finishNodeAnalyzingResultOverlay(analyzingResult)
        }
        return result
    }
}