package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.datafixers.util.Either
import net.minecraft.commands.CommandSourceStack
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator
import kotlin.math.min

interface StringifiableCommandNode {
    @Throws(CommandSyntaxException::class)
    fun `command_crafter$stringifyNode`(
        context: CommandContext<CommandSourceStack>,
        range: StringRange,
        reader: DirectiveStringReader<RawZipResourceCreator>
    ): List<Either<String, RawResource>>

    companion object {
        fun stringifyNodeFromStringRange(context: CommandContext<CommandSourceStack>, range: StringRange) =
            sanitizeMultiline(context.input.substring(range.start, min(range.end, context.input.length)))

        fun sanitizeMultiline(string: String) = string.trimIndent().replace('\n', ' ')
    }
}