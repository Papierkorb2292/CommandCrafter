package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

class FunctionBreakpointLocation(
    val commandSectionLocation: CommandContext<ServerCommandSource>,
    val commandLocationRoot: CommandContext<ServerCommandSource>
)