package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.server.command.ServerCommandSource

interface BreakpointCondition {
    fun checkCondition(source: ServerCommandSource): Boolean
    fun checkHitCondition(source: ServerCommandSource): Boolean
}