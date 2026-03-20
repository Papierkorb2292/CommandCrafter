package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.commands.arguments.item.ItemParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer
import net.papierkorb2292.command_crafter.mixin.editor.processing.ItemParserAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class ItemArgumentAnalyzer : CommandArgumentAnalyzerService<ItemArgument> {
    override val argumentTypes: List<Class<out ItemArgument>>
        get() = listOf(ItemArgument::class.java)

    @Suppress("KotlinConstantConditions")
    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: ItemArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        val parser = ItemParser(reader.resourceCreator.registries)
        (parser as AnalyzingResultDataContainer).`command_crafter$setAnalyzingResult`(result)
        (parser as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        (parser as ItemParserAccessor).callParse(reader)
    }
}