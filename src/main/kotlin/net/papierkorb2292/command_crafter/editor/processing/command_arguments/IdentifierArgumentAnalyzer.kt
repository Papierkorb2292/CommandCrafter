package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.commands.arguments.SerializableArgument
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.processing.AdditionalArgumentTypeData
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.getArgumentOrNull
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor

class IdentifierArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(IdentifierArgument::class.java)

    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        val fileType = reader.resourceCreator.commandTree.getAdditionalData(type, AdditionalArgumentTypeData.packContentFileType)
        if(fileType != null) {
            IdArgumentTypeAnalyzer.analyzeForId(
                context.getArgumentOrNull<Identifier, _>(name) ?: return,
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