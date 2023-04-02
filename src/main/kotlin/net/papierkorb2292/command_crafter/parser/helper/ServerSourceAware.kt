package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.server.command.ServerCommandSource

interface ServerSourceAware {
    fun `command_crafter$setServerCommandSource`(source: ServerCommandSource)
}