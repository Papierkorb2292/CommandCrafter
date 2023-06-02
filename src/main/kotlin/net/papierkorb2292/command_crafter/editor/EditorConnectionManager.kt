package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.helper.CallbackExecutorService
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Supplier

class EditorConnectionManager(private val connectionAcceptor: EditorConnectionAcceptor, private val serverSupplier: Supplier<LanguageServer>) {

    private val connections: ConcurrentMap<LanguageServer, Pair<LanguageClient, Future<Void>>> = ConcurrentHashMap()
    private var connector: Thread? = null

    fun startServer() {
        stopServer()
        connectionAcceptor.start()
        connector = Thread {
            while(connectionAcceptor.isRunning()) {
                val editorConnection = connectionAcceptor.accept()
                handleConnection(editorConnection, serverSupplier.get())
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

    private fun handleConnection(connection: EditorConnection, server: LanguageServer) {
        val launcher = LSPLauncher.createServerLauncher(server, connection.inputStream, connection.outputStream, CallbackExecutorService(
            Executors.newCachedThreadPool()
        ) {
            connections.remove(server)
        }, null)
        if(server is LanguageClientAware) {
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