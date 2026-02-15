package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class StringArgumentAnalyzer : CommandArgumentAnalyzerService<StringArgumentType> {
    override val argumentTypes
        get() = listOf(StringArgumentType::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: StringArgumentType,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        result.semanticTokens.addMultiline(range, TokenType.STRING, 0)
    }
}