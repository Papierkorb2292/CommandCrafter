package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.core.registries.Registries
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator
import net.papierkorb2292.command_crafter.mixin.editor.processing.BlockStateParserAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class BlockStateArgumentAnalyzer : CommandArgumentAnalyzerService<BlockStateArgument> {
    override val argumentTypes: List<Class<out BlockStateArgument>>
        get() = listOf(BlockStateArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: BlockStateArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        val blocks = buildContext.lookup(Registries.BLOCK).get()
        val blockArgumentParser = BlockStateParserAccessor.callInit(blocks, reader, false, true)
        (blockArgumentParser as AnalyzingResultCreator).`command_crafter$setAnalyzingResult`(result)
        (blockArgumentParser as BlockStateParserAccessor).callParse()
    }
}