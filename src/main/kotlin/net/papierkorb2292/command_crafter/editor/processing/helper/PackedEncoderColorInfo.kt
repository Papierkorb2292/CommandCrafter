package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.serialization.Codec
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.Encoder
import net.minecraft.util.ARGB
import net.papierkorb2292.command_crafter.editor.processing.CodecAnalyzingWrapper
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import org.eclipse.lsp4j.*
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.math.cbrt
import kotlin.math.pow

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
                    val colors = additionalSuggestions() + fromPacked(if(hasAlpha) -1 else 0xFFFFFF) // Suggest white so the user sees that they can input a color
                    return colors.stream().map { delegate.encodeStart(ops, it).orThrow }
                }

                override fun <T: Any> suggestionModifier(
                    suggestion: ExtraDecoderBehavior.PossibleValue<T>,
                    ops: DynamicOps<T>,
                ): ExtraDecoderBehavior.PossibleValue<T> =
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

        fun <A> roundColorLab(validColors: Iterable<A>, inputColor: Int, toPacked: (A) -> Int): A {
            val requestedLAB = FloatArray(3)
            val candidateLAB = FloatArray(3)
            rgbToLab(
                ARGB.redFloat(inputColor),
                ARGB.greenFloat(inputColor),
                ARGB.blueFloat(inputColor),
                requestedLAB
            )
            return validColors.minBy { validColor ->
                val validPacked = toPacked(validColor)
                rgbToLab(
                    ARGB.redFloat(validPacked),
                    ARGB.greenFloat(validPacked),
                    ARGB.blueFloat(validPacked),
                    candidateLAB
                )
                var dist = 0f
                for(i in 0 until 3) {
                    val diff = requestedLAB[i] - candidateLAB[i]
                    dist += diff * diff
                }

                dist
            }
        }

        private fun rgbToLab(r: Float, g: Float, b: Float, out: FloatArray) {
            val linearR = r.pow(2.2f)
            val linearG = g.pow(2.2f)
            val linearB = b.pow(2.2f)
            val x = 0.412453f * linearR + 0.357580f * linearG + 0.180423f * linearB
            val y = 0.212671f * linearR + 0.715160f * linearG + 0.072169f * linearB
            val z = 0.019334f * linearR + 0.119193f * linearG + 0.950227f * linearB
            val d65ReferenceX = 0.950489f
            val d65ReferenceY = 1f
            val d65ReferenceZ = 1.088840f
            val delta = 6f/29

            fun f(t: Float): Float =
                if(t > delta * delta * delta) cbrt(t)
                else t / (3 * delta * delta) + 4f/29

            val fY = f(y / d65ReferenceY)
            out[0] = 116 * fY - 16
            out[1] = 500 * (f(x / d65ReferenceX) - fY)
            out[2] = 200 * (fY - f(z / d65ReferenceZ))
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