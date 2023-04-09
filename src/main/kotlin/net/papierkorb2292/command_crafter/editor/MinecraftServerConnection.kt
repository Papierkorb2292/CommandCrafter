package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource

interface MinecraftServerConnection {
    val commandDispatcher: CommandDispatcher<ServerCommandSource>
    val functionPermissionLevel: Int
}