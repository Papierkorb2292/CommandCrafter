package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.ScoreboardStorageFileSystem

interface MinecraftServerConnection {
    val commandDispatcher: CommandDispatcher<SharedSuggestionProvider>
    val functionPermissions: PermissionSet
    val serverLog: Log?
    val commandExecutor: CommandExecutor?
    val debugService: ServerDebugConnectionService?
    val contextCompletionProvider: ContextCompletionProvider?
    val dynamicRegistryManager: RegistryAccess
    val datapackReloader: (() -> Unit)?
    val canReloadWorldgen: Boolean

    fun createScoreboardStorageFileSystem(): ScoreboardStorageFileSystem?
}