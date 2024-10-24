package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.command.CommandSource
import net.minecraft.registry.DynamicRegistryManager
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider

class ClientDummyServerConnection(
    override val commandDispatcher: CommandDispatcher<CommandSource>,
    override val functionPermissionLevel: Int,
    override val serverLog: Log? = null,
    override val commandExecutor: CommandExecutor? = null,
    override val debugService: ServerDebugConnectionService? = null,
    override val contextCompletionProvider: ContextCompletionProvider? = null,
    val dynamicRegistryManagerGetter: () -> DynamicRegistryManager = { ClientCommandCrafter.getLoadedClientsideRegistries().combinedRegistries.combinedRegistryManager }
) : MinecraftServerConnection {
    override val dynamicRegistryManager: DynamicRegistryManager
        get() = dynamicRegistryManagerGetter()
}