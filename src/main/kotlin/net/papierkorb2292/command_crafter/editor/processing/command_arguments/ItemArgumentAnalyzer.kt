package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.SerializableArgument
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.commands.arguments.item.ItemParser
import net.minecraft.commands.suggestions.SuggestionContext
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer
import net.papierkorb2292.command_crafter.mixin.editor.processing.ItemParserAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor

class ItemArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(ItemArgument::class.java)

    @Suppress("KotlinConstantConditions")
    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        val parser = ItemParser(reader.resourceCreator.registries)
        (parser as AnalyzingResultDataContainer).`command_crafter$setAnalyzingResult`(result)
        (parser as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        try {
            (parser as ItemParserAccessor).callParse(reader, DummyVisitor) // With DummyVisitor, because the normal parse() method might throw a NPE if no item can be parsed
        } catch(_: CommandSyntaxException) { }
    }

    private object DummyVisitor : ItemParser.Visitor
}