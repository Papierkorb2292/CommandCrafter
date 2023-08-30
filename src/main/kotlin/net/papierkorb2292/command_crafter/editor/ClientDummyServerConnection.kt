package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService

class ClientDummyServerConnection(
    override val commandDispatcher: CommandDispatcher<ServerCommandSource>,
    override val functionPermissionLevel: Int,
    override val serverLog: Log? = null,
    override val commandExecutor: CommandExecutor? = null,
    override val debugService: ServerDebugConnectionService? = null
) : MinecraftServerConnection