package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

class DispatcherContext(
    val result: Int,
    val successfulForks: Int,
    val forked: Boolean,
    val foundCommand: Boolean,
    val original: CommandContext<ServerCommandSource>,
    val contexts: List<CommandContext<ServerCommandSource>>,
    val next: ArrayList<CommandContext<ServerCommandSource>>?,
    val currentSectionIndex: Int,
    val branchContextGroupEndIndices: List<Int>?,
    val currentContextIndex: Int
)