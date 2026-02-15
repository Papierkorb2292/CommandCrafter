package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class IdentifierArgumentAnalyzer : CommandArgumentAnalyzerService<IdentifierArgument> {
    override val argumentTypes
        get() = listOf(IdentifierArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: IdentifierArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        val fileType = (type as PackContentFileTypeContainer).`command_crafter$getPackContentFileType`()
        if(fileType != null) {
            IdArgumentTypeAnalyzer.analyzeForId(
                context.getArgument(name, Identifier::class.java),
                fileType,
                range,
                result,
                reader
            )
            return
        }
        result.semanticTokens.addMultiline(range, PARAMETER, 0)
    }
}