package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.editor.processing.helper.withUniqueCompletions
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class ParserBasedArgumentAnalyzer : CommandArgumentAnalyzerService<ParserBasedArgument<*>>{
    override val argumentTypes: List<Class<out ParserBasedArgument<*>>>
        get() = listOf(ParserBasedArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: ParserBasedArgument<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        PackratParserAdditionalArgs.analyzingResult.set(PackratParserAdditionalArgs.AnalyzingResultBranchingArgument(result.copyInput()))
        PackratParserAdditionalArgs.setupFurthestAnalyzingResultStart()
        PackratParserAdditionalArgs.allowMalformed.set(true)

        try {
            try {
                type.parse(reader)
            } catch(_: CommandSyntaxException) {}

            val parsedAnalyzingResult = PackratParserAdditionalArgs.analyzingResult.get().analyzingResult
            val furthestAnalyzingResult = PackratParserAdditionalArgs.getAndRemoveFurthestAnalyzingResult() ?: parsedAnalyzingResult
            result.combineWithActual(furthestAnalyzingResult)

            // Use parsedAnalyzingResult, because all potential syntax nodes have been merged into that one.
            // Make completions unique, because packrat parsing can result in duplicated completions.
            result.addContinuouslyMappedPotentialSyntaxNode(AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL, range, parsedAnalyzingResult.withUniqueCompletions())
        } finally {
            PackratParserAdditionalArgs.allowMalformed.remove()
            PackratParserAdditionalArgs.analyzingResult.remove()
            PackratParserAdditionalArgs.furthestAnalyzingResult.remove()
        }
    }
}