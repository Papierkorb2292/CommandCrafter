package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log

interface MinecraftServerConnection {
    val commandDispatcher: CommandDispatcher<ServerCommandSource>
    val functionPermissionLevel: Int
    val serverLog: Log?
    val commandExecutor: CommandExecutor?
}