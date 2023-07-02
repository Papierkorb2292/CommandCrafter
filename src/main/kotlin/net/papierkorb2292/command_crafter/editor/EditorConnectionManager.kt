package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.processing.helper.EditorClientAware
import net.papierkorb2292.command_crafter.helper.CallbackExecutorService
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

class EditorConnectionManager(private val connectionAcceptor: EditorConnectionAcceptor, minecraftServerConnection: MinecraftServerConnection, private val serverCreator: (MinecraftServerConnection) -> MinecraftServerConnectedLanguageServer) {

    private val connections: ConcurrentMap<MinecraftServerConnectedLanguageServer, Pair<LanguageClient, Future<Void>>> = ConcurrentHashMap()
    private var connector: Thread? = null

    var minecraftServerConnection: MinecraftServerConnection = minecraftServerConnection
        set(value) {
            field = value
            for(server in connections.keys) {
                server.setMinecraftServerConnection(value)
            }
        }

    fun startServer() {
        stopServer()
        connectionAcceptor.start()
        connector = Thread {
            while(connectionAcceptor.isRunning()) {
                val editorConnection = connectionAcceptor.accept()
                handleConnection(editorConnection, serverCreator(minecraftServerConnection))
            }
        }.apply { start() }
    }

    fun stopServer() {
        connector?.interrupt()
        connector = null
        for((_, connection) in connections.values) {
            connection.cancel(true)
        }
        connections.clear()
        connectionAcceptor.stop()
    }

    private fun handleConnection(connection: EditorConnection, server: MinecraftServerConnectedLanguageServer) {
        val launcher = Launcher.createLauncher(server, EditorClient::class.java, connection.inputStream, connection.outputStream, CallbackExecutorService(
            Executors.newCachedThreadPool()
        ) {
            connections.remove(server)
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
        connections[server] = launcher.remoteProxy to launcher.startListening()
    }

    fun showMessage(message: MessageParams) {
        for((client, _) in connections.values) {
            client.showMessage(message)
        }
    }
}