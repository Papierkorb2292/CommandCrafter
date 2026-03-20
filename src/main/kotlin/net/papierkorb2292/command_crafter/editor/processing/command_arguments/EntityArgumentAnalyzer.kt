package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class EntityArgumentAnalyzer : CommandArgumentAnalyzerService<EntityArgument> {
    override val argumentTypes: List<Class<out EntityArgument>>
        get() = listOf(EntityArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: EntityArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        val selectorReader = EntitySelectorParser(reader, true)
        @Suppress("KotlinConstantConditions")
        (selectorReader as AnalyzingResultDataContainer).`command_crafter$setAnalyzingResult`(result)
        (selectorReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        selectorReader.parse()
    }
}