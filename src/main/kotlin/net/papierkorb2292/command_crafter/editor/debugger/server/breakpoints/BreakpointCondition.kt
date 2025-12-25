package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.commands.CommandSourceStack

interface BreakpointCondition {
    fun checkCondition(source: CommandSourceStack): Boolean
    fun checkHitCondition(source: CommandSourceStack): Boolean
}