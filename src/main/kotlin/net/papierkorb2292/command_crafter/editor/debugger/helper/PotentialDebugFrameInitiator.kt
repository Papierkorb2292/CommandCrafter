package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

interface PotentialDebugFrameInitiator {
    fun `command_crafter$willInitiateDebugFrame`(context: CommandContext<ServerCommandSource>): Boolean
    fun `command_crafter$isInitializedDebugFrameEmpty`(context: CommandContext<ServerCommandSource>): Boolean
}