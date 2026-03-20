package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.RangeArgument
import net.minecraft.commands.arguments.coordinates.*
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class NumberArgumentAnalyzer : CommandArgumentAnalyzerService<ArgumentType<*>> {
    override val argumentTypes: List<Class<out ArgumentType<*>>>
        get() = listOf(
            IntegerArgumentType::class.java,
            LongArgumentType::class.java,
            FloatArgumentType::class.java,
            DoubleArgumentType::class.java,
            BlockPosArgument::class.java,
            ColumnPosArgument::class.java,
            RotationArgument::class.java,
            Vec3Argument::class.java,
            Vec2Argument::class.java,
            RangeArgument::class.java
        )

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: ArgumentType<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        result.semanticTokens.addMultiline(range, TokenType.NUMBER, 0)
    }
}