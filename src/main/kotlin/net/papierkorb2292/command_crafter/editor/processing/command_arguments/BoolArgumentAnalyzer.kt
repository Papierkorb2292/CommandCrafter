package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class BoolArgumentAnalyzer : CommandArgumentAnalyzerService<BoolArgumentType> {
    override val argumentTypes
        get() = listOf(BoolArgumentType::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: BoolArgumentType,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        result.semanticTokens.addMultiline(range, TokenType.ENUM_MEMBER, 0)
    }
}