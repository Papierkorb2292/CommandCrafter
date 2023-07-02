package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log

class MutableForwardingServerConnection(var delegate: MinecraftServerConnection) : MinecraftServerConnection {
    override val commandDispatcher: CommandDispatcher<ServerCommandSource>
        get() = delegate.commandDispatcher
    override val functionPermissionLevel: Int
        get() = delegate.functionPermissionLevel
    override val serverLog: Log?
        get() = delegate.serverLog
    override val commandExecutor: CommandExecutor?
        get() = delegate.commandExecutor
}