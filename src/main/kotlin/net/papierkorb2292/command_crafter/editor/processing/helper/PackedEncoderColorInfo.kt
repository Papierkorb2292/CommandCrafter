package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.serialization.Codec
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.Encoder
import net.minecraft.util.ARGB
import net.papierkorb2292.command_crafter.editor.processing.CodecAnalyzingWrapper
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import org.eclipse.lsp4j.*
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

class PackedEncoderColorInfo<TNode>(
    override val range: Range,
    packedColor: Int,
    private val hasAlpha: Boolean,
    private val encoder: Encoder<Int>,
    private val ops: DynamicOps<TNode>,
) : ColorInfo {
    companion object {

        fun wrapCodec(delegate: Codec<Int>, hasAlpha: Boolean): Codec<Int> = wrapCodec(delegate, hasAlpha, { it }, { it })

        fun <A> wrapCodec(delegate: Codec<A>, hasAlpha: Boolean, toPacked: (A) -> Int, fromPacked: (Int) -> A, additionalSuggestions: () -> List<A> = ::emptyList): Codec<A> {
            val withColorInfo = CodecAnalyzingWrapper(delegate) { analyzingResult, stringRange, parsed, ops ->
                analyzingResult.colorInfos += PackedEncoderColorInfo(
                    Range(
                        AnalyzingResult.getPositionFromCursor(analyzingResult.mappingInfo.cursorMapper.mapToSource(stringRange.start + analyzingResult.mappingInfo.readSkippingChars), analyzingResult.mappingInfo),
                        AnalyzingResult.getPositionFromCursor(analyzingResult.mappingInfo.cursorMapper.mapToSource(stringRange.end + analyzingResult.mappingInfo.readSkippingChars), analyzingResult.mappingInfo),
                    ),
                    if(hasAlpha) { toPacked(parsed) } else { toPacked(parsed) or 0xFF000000.toInt() },
                    hasAlpha,
                    delegate.comap(fromPacked),
                    ops
                )
            }
            return CodecSuggestionWrapper(withColorInfo, object : CodecSuggestionWrapper.SuggestionsProvider {
                override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                    val colors = additionalSuggestions() + fromPacked(-1) // Suggest white so the user sees that they can input a color
                    return colors.stream().map { delegate.encodeStart(ops, it).orThrow }
                }

                override fun <T: Any> suggestionModifier(
                    suggestion: StringRangeTree.Suggestion<T>,
                    ops: DynamicOps<T>,
                ): StringRangeTree.Suggestion<T> =
                    suggestion.withPreferHex().withCompletionModifier { completionItem ->
                        val color = delegate.parse(ops, suggestion.element)
                            .map { toPacked(it) }
                            .result().orElse(null)?.and(0xFFFFFF) // Doesn't accept alpha
                        completionItem.kind = CompletionItemKind.Color
                        if(color != null) {
                            // VSCode uses detail to preview colors in auto-complete list
                            completionItem.detail = "#" + colorToHex(color, false)
                        }
                    }
            })
        }

        fun colorToHex(color: Int, hasAlpha: Boolean): String {
            return String.format(Locale.ROOT, if(hasAlpha) "%08X" else "%06X", color);
        }
    }

    override val color = Color(
            ARGB.redFloat(packedColor).toDouble(),
            ARGB.greenFloat(packedColor).toDouble(),
            ARGB.blueFloat(packedColor).toDouble(),
            ARGB.alphaFloat(packedColor).toDouble()
        )

    override fun getPresentation(params: ColorPresentationParams): List<ColorPresentation> {
        val packed = ARGB.colorFromFloat(
            if(hasAlpha) params.color.alpha.toFloat() else 0f,
            params.color.red.toFloat(),
            params.color.green.toFloat(),
            params.color.blue.toFloat()
        )
        val encoded = encoder.encodeStart(ops, packed).result().orElse(null) ?: return emptyList()
        val number = ops.getNumberValue(encoded).result().getOrNull()
        val label = if(number is Int) "0x" + colorToHex(number, hasAlpha) else encoded.toString()
        return listOf(ColorPresentation(label))
    }
}