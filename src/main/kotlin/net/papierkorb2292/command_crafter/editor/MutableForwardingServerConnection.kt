package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource

class MutableForwardingServerConnection(var delegate: MinecraftServerConnection) : MinecraftServerConnection {
    override val commandDispatcher: CommandDispatcher<ServerCommandSource>
        get() = delegate.commandDispatcher
    override val functionPermissionLevel: Int
        get() = delegate.functionPermissionLevel
}