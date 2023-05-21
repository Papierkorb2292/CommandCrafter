package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

interface AnalyzingCommandNode {
    @Throws(CommandSyntaxException::class)
    fun `command_crafter$analyze`(
        context: CommandContext<ServerCommandSource>,
        range: StringRange,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
        name: String
    )
}