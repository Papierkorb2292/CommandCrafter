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
import kotlin.math.absoluteValue

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
                AnalyzingResult.getPositionFromCursor(range.start + result.mappingInfo.readSkippingChars, result.mappingInfo),
                AnalyzingResult.getPositionFromCursor(range.end + result.mappingInfo.readSkippingChars, result.mappingInfo)
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
                // so find best ChatFormatting by minimizing distance in HSB space
                val requestedHSB = java.awt.Color.RGBtoHSB(
                    (params.color.red * 255).toInt(),
                    (params.color.green * 255).toInt(),
                    (params.color.blue * 255).toInt(),
                    null
                )
                val label = ChatFormatting.getNames(true, false)
                    .filter { ChatFormatting.getByName(it)!!.color != null }
                    .minBy { name ->
                        val formatting = ChatFormatting.getByName(name)!!
                        val candidateHSB = java.awt.Color.RGBtoHSB(
                            ARGB.red(formatting.color!!),
                            ARGB.green(formatting.color!!),
                            ARGB.blue(formatting.color!!),
                            null
                        )
                        var hueDiff = (requestedHSB[0] - candidateHSB[0]).absoluteValue
                        if(hueDiff > 128)
                            hueDiff = 256 - hueDiff // Wrap-around
                        hueDiff *= 3 // Weigh hue more, because it's a separate slider (otherwise bright, saturated red is unintuitively interpreted as gold)
                        val satDiff = requestedHSB[1] - candidateHSB[1]
                        val brightDiff = requestedHSB[2] - candidateHSB[2]

                        hueDiff*hueDiff + satDiff*satDiff + brightDiff*brightDiff
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
}