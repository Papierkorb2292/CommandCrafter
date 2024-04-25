package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.RootCommandNode
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.*
import net.papierkorb2292.command_crafter.editor.debugger.InitializedEventEmittingMessageWrapper
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutorService

object ClientCommandCrafter : ClientModInitializer {
    override fun onInitializeClient() {
        initializeEditor()
    }

    val editorConnectionManager: EditorConnectionManager = EditorConnectionManager(
        SocketEditorConnectionType(52853), //TODO: Let the user change the port
        ClientDummyServerConnection(
            CommandDispatcher(), 0
        ),
        mapOf(
            "languageServer" to object : EditorConnectionManager.ServiceLauncher {
                override fun launch(
                    serverConnection: MinecraftServerConnection,
                    editorConnection: EditorConnection,
                    executorService: ExecutorService
                ): EditorConnectionManager.LaunchedService {
                    val server = MinecraftLanguageServer(serverConnection)
                    val launcher = Launcher.Builder<CommandCrafterLanguageClient>()
                        .setLocalService(server)
                        .setRemoteInterface(CommandCrafterLanguageClient::class.java)
                        .setInput(editorConnection.inputStream)
                        .setOutput(editorConnection.outputStream)
                        .setExecutorService(executorService)
                        .setExceptionHandler {
                            handleEditorServiceException("languageServer", it)
                        }
                        .create();
                    val launched = launcher.startListening()
                    server.connect(launcher.remoteProxy)
                    launcher.remoteProxy.showMessage(MessageParams(MessageType.Info, "Connected to Minecraft"))
                    return EditorConnectionManager.LaunchedService(server, EditorConnectionManager.ServiceClient(launcher.remoteProxy), launched)
                }
            },
            "debugger" to object : EditorConnectionManager.ServiceLauncher {
                override fun launch(
                    serverConnection: MinecraftServerConnection,
                    editorConnection: EditorConnection,
                    executorService: ExecutorService,
                ): EditorConnectionManager.LaunchedService {
                    val server = MinecraftDebuggerServer(serverConnection)
                    val messageWrapper = InitializedEventEmittingMessageWrapper()
                    val launcher = DebugLauncher.Builder<CommandCrafterDebugClient>()
                        .setLocalService(server)
                        .setRemoteInterface(CommandCrafterDebugClient::class.java)
                        .setInput(editorConnection.inputStream)
                        .setOutput(editorConnection.outputStream)
                        .setExecutorService(executorService)
                        .wrapMessages(messageWrapper)
                        .setExceptionHandler {
                            handleEditorServiceException("debugger", it)
                        }
                        .create();
                    messageWrapper.client = launcher.remoteProxy
                    val launched = launcher.startListening()
                    server.connect(launcher.remoteProxy)
                    return EditorConnectionManager.LaunchedService(server, EditorConnectionManager.ServiceClient(launcher.remoteProxy), launched)
                }

            }
        )
    )

    private fun initializeEditor() {
        val registryWrapperLookup = BuiltinRegistries.createWrapperLookup()
        fun setDefaultServerConnection() {
            @Suppress("UNCHECKED_CAST")
            editorConnectionManager.minecraftServerConnection = ClientDummyServerConnection(
                CommandDispatcher(
                    limitCommandTreeForSource(
                        CommandManager(
                            CommandManager.RegistrationEnvironment.ALL,
                            CommandManager.createRegistryAccess(registryWrapperLookup)
                        ), ServerCommandSource(
                            CommandOutput.DUMMY,
                            Vec3d.ZERO,
                            Vec2f.ZERO,
                            null,
                            2,
                            "",
                            ScreenTexts.EMPTY,
                            null,
                            null
                        )
                    ) as RootCommandNode<ServerCommandSource>
                ),
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
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            editorConnectionManager.leave()
        }

        editorConnectionManager.startServer()
    }

    private fun handleEditorServiceException(serviceName: String, e: Throwable): ResponseError {
        CommandCrafter.LOGGER.error("Error thrown by $serviceName", e)
        var coreException = e;
        if(coreException is RuntimeException)
            coreException = coreException.cause ?: coreException
        if(coreException is InvocationTargetException)
            coreException = coreException.targetException
        return ResponseError(ResponseErrorCode.UnknownErrorCode, coreException.message, null)
    }
}