package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame

data class FunctionCallDebugInfo(val context: CommandContext<ServerCommandSource>, val source: ServerCommandSource, val commandInfo: FunctionDebugFrame.CommandInfo)
