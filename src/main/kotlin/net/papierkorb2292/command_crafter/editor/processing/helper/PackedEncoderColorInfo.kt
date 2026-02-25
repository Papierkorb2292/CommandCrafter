package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.serialization.Codec
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.Encoder
import net.minecraft.util.ARGB
import net.papierkorb2292.command_crafter.editor.processing.CodecAnalyzingWrapper
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Range
import kotlin.jvm.optionals.getOrNull

class PackedEncoderColorInfo<TNode>(
    override val range: Range,
    packedColor: Int,
    private val hasAlpha: Boolean,
    private val encoder: Encoder<Int>,
    private val ops: DynamicOps<TNode>
) : ColorInfo {
    companion object {
        fun <A> wrapCodec(delegate: Codec<A>, hasAlpha: Boolean, toPacked: (A) -> Int, fromPacked: (Int) -> A) = CodecAnalyzingWrapper(delegate) { analyzingResult, stringRange, parsed, ops ->
            analyzingResult.colorInfos += PackedEncoderColorInfo(
                Range(
                    AnalyzingResult.getPositionFromCursor(stringRange.start + analyzingResult.mappingInfo.readSkippingChars, analyzingResult.mappingInfo),
                    AnalyzingResult.getPositionFromCursor(stringRange.end + analyzingResult.mappingInfo.readSkippingChars, analyzingResult.mappingInfo),
                ),
                if(hasAlpha) { toPacked(parsed) } else { toPacked(parsed) or 0xFF000000.toInt() },
                hasAlpha,
                delegate.comap(fromPacked),
                ops
            )
        }

        fun colorToHex(color: Int, hasAlpha: Boolean): String {
            val string = color.toUInt().toString(16).uppercase()
            val formattedLength = if(hasAlpha) 8 else 6
            return "0".repeat(formattedLength - string.length) + string
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