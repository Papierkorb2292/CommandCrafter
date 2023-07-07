package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.helper.EditorClientAware
import net.papierkorb2292.command_crafter.helper.CallbackExecutorService
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

class EditorConnectionManager(
    private val connectionAcceptor: EditorConnectionAcceptor,
    minecraftServerConnection: MinecraftServerConnection,
    private val serviceCreators: Map<String, (MinecraftServerConnection) -> EditorService>
) {

    private val runningServices: ConcurrentMap<EditorService, Pair<LanguageClient, Future<Void>>> = ConcurrentHashMap()
    private var connector: Thread? = null

    var minecraftServerConnection: MinecraftServerConnection = minecraftServerConnection
        set(value) {
            field = value
            for(server in runningServices.keys) {
                server.setMinecraftServerConnection(value)
            }
        }

    fun startServer() {
        stopServer()
        connectionAcceptor.start()
        connector = Thread {
            while(connectionAcceptor.isRunning()) {
                val editorConnection = connectionAcceptor.accept()
                try {
                    handleConnection(editorConnection)
                } catch(e: Exception) {
                    editorConnection.close()
                    CommandCrafter.LOGGER.error("Error while connecting to editor", e)
                }
            }
        }.apply { start() }
    }

    private fun handleConnection(editorConnection: EditorConnection) {
        val connectorMessageReader = StreamMessageProducer(
            editorConnection.inputStream,
            DebugMessageJsonHandler(
                mapOf(
                    "connectToService" to JsonRpcMethod.notification(
                        "connectToService",
                        String::class.java
                    )
                )
            )
        )
        connectorMessageReader.listen {
            if(it !is DebugRequestMessage) {
                return@listen
            }
            if(it.method != "connectToService") {
                return@listen
            }
            val serviceName = it.params as String
            val serviceCreator = serviceCreators[serviceName]
            if(serviceCreator != null) {
                connectorMessageReader.close()
                startService(editorConnection, serviceCreator(minecraftServerConnection))
            }
        }
    }

    fun stopServer() {
        connector?.interrupt()
        connector = null
        for((_, connection) in runningServices.values) {
            connection.cancel(true)
        }
        runningServices.clear()
        connectionAcceptor.stop()
    }

    private fun startService(connection: EditorConnection, server: EditorService) {
        val launcher = Launcher.createLauncher(server, EditorClient::class.java, connection.inputStream, connection.outputStream, CallbackExecutorService(
            Executors.newCachedThreadPool()
        ) {
            runningServices.remove(server)
        }, null)
        if(server is LanguageClientAware) {
            server.connect(launcher.remoteProxy)
        }
        if(server is EditorClientAware) {
            server.connect(launcher.remoteProxy)
        }
        if(server is RemoteEndpointAware) {
            server.setRemoteEndpoint(launcher.remoteEndpoint)
        }
        launcher.remoteProxy.showMessage(MessageParams(MessageType.Info, "Connected to Minecraft"))
        runningServices[server] = launcher.remoteProxy to launcher.startListening()
    }

    fun showMessage(message: MessageParams) {
        for((client, _) in runningServices.values) {
            client.showMessage(message)
        }
    }
}