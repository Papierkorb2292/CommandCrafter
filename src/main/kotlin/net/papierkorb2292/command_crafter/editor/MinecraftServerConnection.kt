package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.command.CommandSource
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService

interface MinecraftServerConnection {
    val commandDispatcher: CommandDispatcher<CommandSource>
    val functionPermissionLevel: Int
    val serverLog: Log?
    val commandExecutor: CommandExecutor?
    val debugService: ServerDebugConnectionService?
}