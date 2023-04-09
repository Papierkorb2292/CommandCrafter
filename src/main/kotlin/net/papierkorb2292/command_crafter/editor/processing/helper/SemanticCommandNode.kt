package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.processing.SemanticResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

interface SemanticCommandNode {
    @Throws(CommandSyntaxException::class)
    fun `command_crafter$createSemanticTokens`(
        context: CommandContext<ServerCommandSource>,
        range: StringRange,
        reader: DirectiveStringReader<SemanticResourceCreator>,
        tokens: SemanticTokensBuilder
    )
}