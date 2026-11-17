package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.suggestions.SuggestionContext

interface CustomCompletionsCommandNode {
    fun `command_crafter$hasCustomCompletions`(context: CommandContext<SuggestionContext>, name: String): Boolean
}