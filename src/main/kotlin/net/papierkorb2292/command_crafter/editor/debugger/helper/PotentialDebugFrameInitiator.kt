package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack

interface PotentialDebugFrameInitiator {
    fun `command_crafter$willInitiateDebugFrame`(context: CommandContext<CommandSourceStack>): Boolean
    fun `command_crafter$isInitializedDebugFrameEmpty`(context: CommandContext<CommandSourceStack>): Boolean
}