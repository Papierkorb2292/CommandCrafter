package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack

class FunctionBreakpointLocation(
    val commandSectionLocation: CommandContext<CommandSourceStack>,
    val commandLocationRoot: CommandContext<CommandSourceStack>
)