package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import net.minecraft.command.CommandAction
import net.minecraft.command.CommandExecutionContext
import net.minecraft.command.Frame
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

object ExitDebugFrameCommandAction : CommandAction<ServerCommandSource> {
    override fun execute(context: CommandExecutionContext<ServerCommandSource>, frame: Frame) {
        PauseContext.currentPauseContext.get()?.popDebugFrame()
    }
}