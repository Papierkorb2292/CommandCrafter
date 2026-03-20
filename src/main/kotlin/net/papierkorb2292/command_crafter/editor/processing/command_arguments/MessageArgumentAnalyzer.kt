package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.MessageArgument
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.ENUM_MEMBER
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class MessageArgumentAnalyzer : CommandArgumentAnalyzerService<MessageArgument> {
    override val argumentTypes
        get() = listOf(MessageArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: MessageArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        // TODO: Analyze selectors
        result.semanticTokens.addMultiline(range, ENUM_MEMBER, 0)
    }
}