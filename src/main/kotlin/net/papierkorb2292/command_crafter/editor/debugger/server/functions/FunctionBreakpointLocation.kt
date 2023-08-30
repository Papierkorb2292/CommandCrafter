package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.context.CommandContextBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier

class FunctionBreakpointLocation(
    val function: Identifier,
    val commandSectionLocation: CommandContextBuilder<ServerCommandSource>,
    val commandLocationRoot: CommandContextBuilder<ServerCommandSource>
) {
    constructor(function: Identifier, command: ParseResults<ServerCommandSource>)
            : this(function, command.context, command.context)
    constructor(function: Identifier, commandSectionLocation: CommandContextBuilder<ServerCommandSource>, commandLocation: ParseResults<ServerCommandSource>)
            : this(function, commandSectionLocation, commandLocation.context)
}