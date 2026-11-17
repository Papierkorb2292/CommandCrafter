package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.arguments.SerializableArgument
import net.minecraft.commands.arguments.SlotSourceArgument
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.world.inventory.SlotRanges
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.mixin.editor.processing.SlotSourceArgumentAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor

class SlotSourceArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(SlotSourceArgument::class.java)

    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        val slotRange = SlotRanges.tryRead(reader)
        if(slotRange != null) {
            result.semanticTokens.addMultiline(range, TokenType.PARAMETER, 0)
            ResourceOrIdArgumentAnalyzer.analyzeEmpty(
                (type.argument() as SlotSourceArgumentAccessor).holderArgument,
                result,
                reader,
                range.start,
                analyzingExecutor
            )
            return
        }

        reader.cursor = range.start
        ResourceOrIdArgumentAnalyzer.analyzeReader(
            (type.argument() as SlotSourceArgumentAccessor).holderArgument,
            result,
            reader,
            range,
            analyzingExecutor
        )
    }
}