package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.IdentifierException
import net.minecraft.commands.arguments.SerializableArgument
import net.minecraft.commands.arguments.item.FunctionArgument
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer.analyzeForId
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType.Companion.FUNCTIONS_FILE_TYPE
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType.Companion.FUNCTION_TAGS_FILE_TYPE
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.getArgumentOrNull
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.AnalyzedFunctionArgument
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage.Companion.isReaderInlineResources

class FunctionArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(FunctionArgument::class.java)

    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        val argument = context.getArgumentOrNull<FunctionArgument.Result, _>(name) ?: return
        if(argument is AnalyzedFunctionArgument) {
            result.combineWith(argument.result)
            return
        }
        val stringArgument = range.get(reader.string)
        val isTag = stringArgument.startsWith("#")
        try {
            // Use `read` because there might be trailing data that isn't supposed to throw an error
            val id = Identifier.read(StringReader(if(isTag) stringArgument.substring(1) else stringArgument))
            val fileType = if(isTag) FUNCTION_TAGS_FILE_TYPE else FUNCTIONS_FILE_TYPE
            analyzeForId(id, fileType, range, result, reader)
        } catch(_: IdentifierException) { }
        if(isReaderInlineResources(reader)) {
            val readerCopy: DirectiveStringReader<AnalyzingResourceCreator> = reader.copy()
            readerCopy.cursor = range.start
            val function = VanillaLanguage.analyzeImprovedFunctionReference(readerCopy, true)
            if(function != null) result.combineWith(function.result)
        }
    }

    override fun hasCustomCompletions(context: CommandContext<SuggestionContext>, name: String) = true
}