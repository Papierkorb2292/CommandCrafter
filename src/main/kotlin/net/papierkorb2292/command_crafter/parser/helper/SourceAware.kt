package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.command.CommandSource

interface SourceAware {
    fun `command_crafter$setCommandSource`(source: CommandSource)
}