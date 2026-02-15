package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class DimensionArgumentAnalyzer : CommandArgumentAnalyzerService<DimensionArgument> {
    override val argumentTypes
        get() = listOf(DimensionArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: DimensionArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        IdArgumentTypeAnalyzer.analyzeForId(
            context.getArgument(name, Identifier::class.java)!!,
            PackContentFileType.getOrCreateTypeForDynamicRegistry(Registries.DIMENSION),
            range,
            result,
            reader
        )
    }
}