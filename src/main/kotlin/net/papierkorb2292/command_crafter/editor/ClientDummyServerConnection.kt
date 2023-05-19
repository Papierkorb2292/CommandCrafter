package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource

class ClientDummyServerConnection(
    override val commandDispatcher: CommandDispatcher<ServerCommandSource>,
    override val functionPermissionLevel: Int
) : MinecraftServerConnection