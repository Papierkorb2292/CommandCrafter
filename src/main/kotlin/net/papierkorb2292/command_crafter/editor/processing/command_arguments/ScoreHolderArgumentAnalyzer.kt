package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ScoreHolderArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class ScoreHolderArgumentAnalyzer : CommandArgumentAnalyzerService<ScoreHolderArgument> {
    override val argumentTypes: List<Class<out ScoreHolderArgument>>
        get() = listOf(ScoreHolderArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: ScoreHolderArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        if(!reader.canRead() || reader.peek() != '@') {
            result.semanticTokens.addMultiline(range, PARAMETER, 0)
            return
        }
        val selectorReader = EntitySelectorParser(reader, true)
        @Suppress("KotlinConstantConditions")
        (selectorReader as AnalyzingResultDataContainer).`command_crafter$setAnalyzingResult`(result)
        (selectorReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        selectorReader.parse()
    }
}