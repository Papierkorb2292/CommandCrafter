package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.arguments.*
import net.minecraft.commands.arguments.coordinates.*
import net.minecraft.commands.suggestions.SuggestionContext
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor

class NumberArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(
            IntegerArgument::class.java,
            LongArgument::class.java,
            FloatArgument::class.java,
            DoubleArgument::class.java,
            BlockPosArgument::class.java,
            ColumnPosArgument::class.java,
            RotationArgument::class.java,
            Vec3Argument::class.java,
            Vec2Argument::class.java,
            RangeArgument::class.java
        )

    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        result.semanticTokens.addMultiline(range, TokenType.NUMBER, 0)
    }
}