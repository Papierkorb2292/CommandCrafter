package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.SerializableArgument
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.core.registries.Registries
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator
import net.papierkorb2292.command_crafter.mixin.editor.processing.BlockStateParserAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor

class BlockPredicateArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(BlockPredicateArgument::class.java)

    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        val blocks = reader.resourceCreator.registries.lookup(Registries.BLOCK).get()
        val blockArgumentParser = BlockStateParserAccessor.callInit(blocks, reader, true, true)
        (blockArgumentParser as AnalyzingResultCreator).`command_crafter$setAnalyzingResult`(result)
        try {
            (blockArgumentParser as BlockStateParserAccessor).callParse()
        } catch(_: CommandSyntaxException) { }
    }
}