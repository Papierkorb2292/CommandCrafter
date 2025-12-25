package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.commands.SharedSuggestionProvider

interface SourceAware {
    fun `command_crafter$setCommandSource`(source: SharedSuggestionProvider)
}