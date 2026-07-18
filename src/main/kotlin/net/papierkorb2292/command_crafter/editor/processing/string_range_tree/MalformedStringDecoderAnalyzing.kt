package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import net.minecraft.util.CompilableString
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import net.papierkorb2292.command_crafter.mixin.editor.processing.CompilableStringCommandParserHelperAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range
import java.util.*
import kotlin.jvm.optionals.getOrNull

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

    fun <A> wrapCodecWithError(delegate: Codec<A>, errorProvider: Decoder<Optional<kotlin.Pair<Int, String>>>, errorIsWarning: Boolean = false): Codec<A> = object : Codec<A> {
        override fun <T : Any> encode(input: A, ops: DynamicOps<T>, prefix: T): DataResult<T> =
            delegate.encode(input, ops, prefix)

        override fun <T : Any> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<A, T>> {
            val result = delegate.decode(ops, input)
            val error = errorProvider.decode(ops, input).result().getOrNull()?.first?.getOrNull()
            onParsedGeneric(Dynamic(ops, input), error?.first ?: Int.MAX_VALUE, error?.second,  errorIsWarning)
            return result
        }
    }

    fun <A : Any> wrapCommandParserHelper(delegate: CompilableString.CommandParserHelper<A>): CompilableString.CommandParserHelper<A> {
        @Suppress("UNCHECKED_CAST")
        val accessor = delegate as CompilableStringCommandParserHelperAccessor<A>
        return object : CompilableString.CommandParserHelper<A>() {
            override fun parse(reader: StringReader): A {
                try {
                    val result = accessor.callParse(reader)
                    onParsed()
                    return result
                } catch(e: CommandSyntaxException) {
                    onParsed(e.cursor, e.message)
                    throw e
                }
            }

            override fun errorMessage(original: String, exception: CommandSyntaxException): String =
                accessor.callErrorMessage(original, exception)

        }
    }

    fun onParsed(errorCursor: Int = Int.MAX_VALUE, errorMsg: String? = null) {
        val dynamic = codecInput.getOrNull() ?: return
        onParsedGeneric(dynamic, errorCursor, errorMsg)
    }

    private fun <T : Any> onParsedGeneric(dynamic: Dynamic<T>, errorCursor: Int, errorMsg: String?, errorIsWarning: Boolean = false) {
        val extraBehavior = ExtraDecoderBehavior.getCurrentBehavior(dynamic.ops)
        val originalReader = extraBehavior?.reader ?: return
        if(errorMsg != null && !errorIsWarning)
            extraBehavior.markStringParseError(dynamic.value)
        val context = contextGetter(dynamic)
        extraBehavior.nodeAnalyzingTracker?.registerCallback(dynamic.value) { analyzingBehavior ->
            val stringContent = analyzingBehavior.stringContentGetter.invoke() ?: return@registerCallback
            val analyzingResult = analyzingBehavior.createStringAnalyzingResultOverlay(stringContent)

            val directiveReader = DirectiveStringReader(
                analyzingResult.mappingInfo,
                originalReader.dispatcher,
                originalReader.resourceCreator.copyInput().apply {
                    stringContent.cursorMapper.mapAllToTargetSorted(macroTargetCursors, false)
                }
            )
            directiveReader.toCompleted()
            directiveReader.string = stringContent.content
            directiveReader.cursor = 0

            try {
                analyzer.analyze(context, analyzingResult, extraBehavior, directiveReader, stringContent, analyzingBehavior)
            } catch(e: CommandSyntaxException) {
                CommandCrafter.LOGGER.debug("Error analyzing string content '${stringContent.content}'", e)
            }

            if(errorMsg != null) {
                val mappingInfo = analyzingResult.mappingInfo
                val diagnostic = Diagnostic().apply {
                    message = errorMsg
                    range = Range(
                        AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(errorCursor + mappingInfo.readSkippingChars, false), mappingInfo, true),
                        AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(stringContent.content.length + mappingInfo.readSkippingChars, false), mappingInfo, true)
                    )
                    if(errorIsWarning)
                        severity = DiagnosticSeverity.Warning
                }
                analyzingResult.diagnostics.add(diagnostic)
            }
            analyzingBehavior.finishNodeAnalyzingResultOverlay(analyzingResult, errorCursor, stringContent)
        }
    }

    fun interface StringAnalyzer<TContext> {
        fun analyze(context: TContext, result: AnalyzingResult, behavior: ExtraDecoderBehavior<*>, reader: DirectiveStringReader<AnalyzingResourceCreator>, string: StringContent, analyzingBehavior: ExtraDecoderBehavior.NodeAnalyzingBehavior<*>)
    }
}