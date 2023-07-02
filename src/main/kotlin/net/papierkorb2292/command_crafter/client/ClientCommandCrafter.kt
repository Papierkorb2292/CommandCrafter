package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.server.command.CommandManager
import net.papierkorb2292.command_crafter.editor.*
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType

object ClientCommandCrafter : ClientModInitializer {
    override fun onInitializeClient() {
        initializeEditor()
    }

    val editorConnectionManager: EditorConnectionManager = EditorConnectionManager(
        SocketEditorConnectionType(52853), //TODO: Let the user change the port
        ClientDummyServerConnection(
            CommandDispatcher(), 0
        )
    ) { MinecraftLanguageServer(it) }

    private fun initializeEditor() {
        val registryWrapperLookup = BuiltinRegistries.createWrapperLookup()
        fun setDefaultServerConnection() {
            editorConnectionManager.minecraftServerConnection = ClientDummyServerConnection(
                CommandManager(
                    CommandManager.RegistrationEnvironment.ALL,
                    CommandManager.createRegistryAccess(registryWrapperLookup)
                ).dispatcher,
                2
            )
        }

        NetworkServerConnection.registerClientPacketHandlers()
        setDefaultServerConnection()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            NetworkServerConnection.requestAndCreate().thenAccept {
                editorConnectionManager.minecraftServerConnection = it
                editorConnectionManager.showMessage(MessageParams(MessageType.Info, "Successfully connected to Minecraft server"))
            }.exceptionally {
                editorConnectionManager.showMessage(MessageParams(MessageType.Warning, "Connecting to Minecraft server failed, keeping clientside connection: $it"))
                null
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            setDefaultServerConnection()
        }

        editorConnectionManager.startServer()
    }
}