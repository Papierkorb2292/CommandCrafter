package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.command.CommandSource
import net.minecraft.command.permission.LeveledPermissionPredicate
import net.minecraft.command.permission.PermissionPredicate
import net.minecraft.registry.DynamicRegistryManager
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.ScoreboardStorageFileSystem

class ClientDummyServerConnection(
    override val commandDispatcher: CommandDispatcher<CommandSource>,
    override val functionPermissions: PermissionPredicate,
    override val serverLog: Log? = null,
    override val commandExecutor: CommandExecutor? = null,
    override val debugService: ServerDebugConnectionService? = null,
    override val contextCompletionProvider: ContextCompletionProvider? = null,
    val dynamicRegistryManagerGetter: () -> DynamicRegistryManager = { ClientCommandCrafter.getLoadedClientsideRegistries().combinedRegistries.combinedRegistryManager },
    val scoreboardStorageFileSystemGetter: () -> ScoreboardStorageFileSystem? = { null },
    override val datapackReloader: (() -> Unit)? = null
) : MinecraftServerConnection {
    override val dynamicRegistryManager: DynamicRegistryManager
        get() = dynamicRegistryManagerGetter()

    override fun createScoreboardStorageFileSystem() = scoreboardStorageFileSystemGetter()
}