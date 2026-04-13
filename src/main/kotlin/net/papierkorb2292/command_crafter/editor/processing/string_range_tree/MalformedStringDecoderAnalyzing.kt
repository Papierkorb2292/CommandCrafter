package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Range

class MalformedStringDecoderAnalyzing<TContext>(private val contextGetter: (Dynamic<out Any>) -> TContext, private val analyzer: StringAnalyzer<TContext>) {
    private val codecInput = ThreadLocal<Dynamic<out Any>>()

    fun <A> wrapCodec(delegate: Codec<A>): Codec<A> = object : Codec<A> {
        override fun <T : Any> encode(input: A, ops: DynamicOps<T>, prefix: T): DataResult<T> =
            delegate.encode(input, ops, prefix)

        override fun <T : Any> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<A, T>> =
            codecInput.runWithValueSwap(Dynamic(ops, input)) {
                delegate.decode(ops, input)
            }
    }

    fun onParsed(errorCursor: Int = Int.MAX_VALUE, errorMsg: String? = null) {
        val dynamic = codecInput.getOrNull() ?: return
        onParsedGeneric(dynamic, errorCursor, errorMsg)
    }

    private fun <T : Any> onParsedGeneric(dynamic: Dynamic<T>, errorCursor: Int, errorMsg: String?) {
        val extraBehavior = ExtraDecoderBehavior.getCurrentBehavior(dynamic.ops) ?: return
        if(errorMsg != null)
            extraBehavior.markStringParseError(dynamic.value)
        val context = contextGetter(dynamic)
        extraBehavior.nodeAnalyzingTracker?.registerCallback(dynamic.value) { analyzingBehavior ->
            val stringContent = analyzingBehavior.stringContentGetter.invoke() ?: return@registerCallback
            val analyzingResult = analyzingBehavior.createStringAnalyzingResultOverlay(stringContent)

            analyzer.analyze(context, analyzingResult, extraBehavior, stringContent)

            if(errorMsg != null) {
                val mappingInfo = analyzingResult.mappingInfo
                val diagnostic = Diagnostic().apply {
                    message = errorMsg
                    range = Range(
                        AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(errorCursor + mappingInfo.readSkippingChars, false), mappingInfo, true),
                        AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(stringContent.content.length + mappingInfo.readSkippingChars, false), mappingInfo, true)
                    )
                }
                analyzingResult.diagnostics.add(diagnostic)
            }
            analyzingBehavior.finishNodeAnalyzingResultOverlay(analyzingResult, errorCursor, stringContent)
        }
    }

    fun interface StringAnalyzer<TContext> {
        fun analyze(context: TContext, result: AnalyzingResult, behavior: ExtraDecoderBehavior<*>, stringContent: StringContent)
    }
}