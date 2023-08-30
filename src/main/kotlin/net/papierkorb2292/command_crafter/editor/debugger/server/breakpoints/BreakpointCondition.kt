package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

interface BreakpointCondition {
    fun checkCondition(context: CommandContext<ServerCommandSource>): Boolean
    fun checkHitCondition(context: CommandContext<ServerCommandSource>): Boolean
}