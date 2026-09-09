package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.TeamColorArgument
import net.minecraft.util.ARGB
import net.minecraft.world.scores.TeamColor
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.ColorInfo
import net.papierkorb2292.command_crafter.editor.processing.helper.PackedEncoderColorInfo
import net.papierkorb2292.command_crafter.editor.processing.helper.getArgumentOrNull
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor
import org.eclipse.lsp4j.*

class TeamColorArgumentAnalyzer : CommandArgumentAnalyzerService<TeamColorArgument> {
    override val argumentTypes
        get() = listOf(TeamColorArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: TeamColorArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        val teamColor = context.getArgumentOrNull<TeamColor, _>(name) ?: return
        val packedColor = teamColor.rgb()
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

                // TeamColorArgument only supports some discrete value,
                // so find best TeamColor by minimizing distance in LAB space (I'm not doing overkill, you're doing overkill)
                val color = PackedEncoderColorInfo.roundColorLab(
                    TeamColor.VALUES,
                    ARGB.colorFromFloat(0f, params.color.red.toFloat(), params.color.green.toFloat(), params.color.blue.toFloat()),
                    TeamColor::rgb
                )
                return listOf(ColorPresentation(color.serializedName))
            }
        }
        result.semanticTokens.addMultiline(range, TokenType.PARAMETER, 0)
    }

    override fun modifyVanillaCompletion(completion: CompletionItem) {
        val color = TeamColor.byName(completion.label)?.rgb() ?: return
        completion.kind = CompletionItemKind.Color
        completion.detail = "#" + PackedEncoderColorInfo.colorToHex(color, false)
    }
}