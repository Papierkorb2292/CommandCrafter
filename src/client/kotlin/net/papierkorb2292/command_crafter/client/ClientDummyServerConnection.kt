package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.ScoreboardStorageFileSystem

class ClientDummyServerConnection(
    override val commandDispatcher: CommandDispatcher<SharedSuggestionProvider>,
    override val functionPermissions: PermissionSet,
    override val serverLog: Log? = null,
    override val commandExecutor: CommandExecutor? = null,
    override val debugService: ServerDebugConnectionService? = null,
    override val contextCompletionProvider: ContextCompletionProvider? = null,
    val dynamicRegistryManagerGetter: () -> RegistryAccess = { ClientCommandCrafter.getLoadedClientsideRegistries().combinedRegistries.compositeAccess() },
    val scoreboardStorageFileSystemGetter: () -> ScoreboardStorageFileSystem? = { null },
    override val datapackReloader: (() -> Unit)? = null,
    override val canReloadWorldgen: Boolean = false
) : MinecraftServerConnection {
    override val dynamicRegistryManager: RegistryAccess
        get() = dynamicRegistryManagerGetter()

    override fun createScoreboardStorageFileSystem() = scoreboardStorageFileSystemGetter()
}