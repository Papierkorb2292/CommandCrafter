package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.HexColorArgument
import net.minecraft.util.ARGB
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.ColorInfo
import net.papierkorb2292.command_crafter.editor.processing.helper.PackedEncoderColorInfo
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Range

class HexColorArgumentAnalyzer : CommandArgumentAnalyzerService<HexColorArgument> {
    override val argumentTypes
        get() = listOf(HexColorArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: HexColorArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        val packedColor = context.getArgument(name, Int::class.java)
        result.colorInfos += object : ColorInfo {
            override val range = Range(
                AnalyzingResult.getPositionFromCursor(result.mappingInfo.cursorMapper.mapToSource(range.start + result.mappingInfo.readSkippingChars), result.mappingInfo),
                AnalyzingResult.getPositionFromCursor(result.mappingInfo.cursorMapper.mapToSource(range.end + result.mappingInfo.readSkippingChars), result.mappingInfo)
            )
            override val color = Color(
                ARGB.redFloat(packedColor).toDouble(),
                ARGB.greenFloat(packedColor).toDouble(),
                ARGB.blueFloat(packedColor).toDouble(),
                ARGB.alphaFloat(packedColor).toDouble()
            )

            override fun getPresentation(params: ColorPresentationParams): List<ColorPresentation> {
                val packed = ARGB.colorFromFloat(
                    0f,
                    params.color.red.toFloat(),
                    params.color.green.toFloat(),
                    params.color.blue.toFloat()
                )
                val label = PackedEncoderColorInfo.colorToHex(packed, false)
                return listOf(ColorPresentation(label))
            }
        }
        result.semanticTokens.addMultiline(range, TokenType.PARAMETER, 0)
    }
}