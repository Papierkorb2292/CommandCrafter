package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.NbtPathArgument
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class NbtPathArgumentAnalyzer : CommandArgumentAnalyzerService<NbtPathArgument> {
    override val argumentTypes
        get() = listOf(NbtPathArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: NbtPathArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        CommandArgumentAnalyzerService.currentAnalyzingResult.runWithValue(result) {
            type.parse(reader)
        }
    }
}