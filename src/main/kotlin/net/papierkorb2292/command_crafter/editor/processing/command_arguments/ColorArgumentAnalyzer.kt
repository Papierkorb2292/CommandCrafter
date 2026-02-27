package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ColorArgument
import net.minecraft.util.ARGB
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.ColorInfo
import net.papierkorb2292.command_crafter.editor.processing.helper.PackedEncoderColorInfo
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.*
import kotlin.math.cbrt
import kotlin.math.pow

class ColorArgumentAnalyzer : CommandArgumentAnalyzerService<ColorArgument> {
    override val argumentTypes
        get() = listOf(ColorArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: ColorArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        val formatting = context.getArgument(name, ChatFormatting::class.java)
        val packedColor = formatting.color!!
        result.colorInfos += object : ColorInfo {
            override val range = Range(
                AnalyzingResult.getPositionFromCursor(result.mappingInfo.cursorMapper.mapToSource(range.start + result.mappingInfo.readSkippingChars), result.mappingInfo),
                AnalyzingResult.getPositionFromCursor(result.mappingInfo.cursorMapper.mapToSource(range.end + result.mappingInfo.readSkippingChars), result.mappingInfo)
            )
            override val color = Color(
                ARGB.redFloat(packedColor).toDouble(),
                ARGB.greenFloat(packedColor).toDouble(),
                ARGB.blueFloat(packedColor).toDouble(),
                1.0
            )

            override fun getPresentation(params: ColorPresentationParams): List<ColorPresentation> {
                val isWaypointColor = context.nodes.firstOrNull()?.node == reader.dispatcher.root.getChild("waypoint")
                if(isWaypointColor) {
                    // Waypoints have a different argument for hex values
                    val packed = ARGB.colorFromFloat(
                        0f,
                        params.color.red.toFloat(),
                        params.color.green.toFloat(),
                        params.color.blue.toFloat()
                    )
                    val label = PackedEncoderColorInfo.colorToHex(packed, false)
                    return listOf(ColorPresentation(label).apply {
                        // Insert 'hex ' literal
                        additionalTextEdits = listOf(TextEdit(Range(params.range.start, params.range.start), "hex "))
                    })
                }

                // ColorArgument only supports some discrete value,
                // so find best ChatFormatting by minimizing distance in LAB space (I'm not doing overkill, you're doing overkill)

                val requestedLAB = FloatArray(3)
                val candidateLAB = FloatArray(3)
                rgbToLab(
                    params.color.red.toFloat(),
                    params.color.green.toFloat(),
                    params.color.blue.toFloat(),
                    requestedLAB
                )
                val label = ChatFormatting.getNames(true, false)
                    .filter { ChatFormatting.getByName(it)!!.color != null }
                    .minBy { name ->
                        val formatting = ChatFormatting.getByName(name)!!
                        rgbToLab(
                            ARGB.redFloat(formatting.color!!),
                            ARGB.greenFloat(formatting.color!!),
                            ARGB.blueFloat(formatting.color!!),
                            candidateLAB
                        )
                        var dist = 0f
                        for(i in 0 until 3) {
                            val diff = requestedLAB[i] - candidateLAB[i]
                            dist += diff * diff
                        }

                        dist
                    }
                return listOf(ColorPresentation(label))
            }
        }
        result.semanticTokens.addMultiline(range, TokenType.PARAMETER, 0)
    }

    override fun modifyVanillaCompletion(completion: CompletionItem) {
        val color = ChatFormatting.getByName(completion.label)?.color ?: return
        completion.kind = CompletionItemKind.Color
        completion.detail = "#" + PackedEncoderColorInfo.colorToHex(color, false)
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