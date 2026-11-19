package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.SerializableArgument
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

class ParserBasedArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(ParserBasedArgument::class.java)

    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        PackratParserAdditionalArgs.analyzingResult.set(PackratParserAdditionalArgs.AnalyzingResultBranchingArgument(result.copyInput()))
        PackratParserAdditionalArgs.setupFurthestAnalyzingResultStart()
        PackratParserAdditionalArgs.allowMalformed.set(true)

        try {
            try {
                type.parse(reader)
            } catch(_: CommandSyntaxException) {}
            PackratParserAdditionalArgs.popAnalyzingResult(result, range)
        } finally {
            PackratParserAdditionalArgs.allowMalformed.remove()
            PackratParserAdditionalArgs.analyzingResult.remove()
            PackratParserAdditionalArgs.furthestAnalyzingResult.remove()
        }
    }

    override fun modifyVanillaCompletion(completion: CompletionItem) {
        completion.kind = CompletionItemKind.Keyword
    }
}