package net.papierkorb2292.command_crafter.editor

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Supplier

class EditorConnectionManager(private val connectionAcceptor: EditorConnectionAcceptor, private val serverSupplier: Supplier<LanguageServer>) {

    private val connections: MutableList<Future<Void>> = ArrayList()
    private var connector: CompletableFuture<Void>? = null

    fun startServer() {
        stopServer()
        connectionAcceptor.start()
        connector = CompletableFuture.runAsync {
            while(connectionAcceptor.isRunning()) {
                val editorConnection = connectionAcceptor.accept()
                handleConnection(editorConnection, serverSupplier.get())
            }
        }
    }

    fun stopServer() {
        connector?.cancel(true)
        connector = null
        for(connection in connections) {
            connection.cancel(true)
        }
        connections.clear()
        connectionAcceptor.stop()
    }

    private fun handleConnection(connection: EditorConnection, server: LanguageServer) {
        val launcher = LSPLauncher.createServerLauncher(server, connection.inputStream, connection.outputStream)
        if(server is LanguageClientAware) {
            server.connect(launcher.remoteProxy)
        }
        if(server is RemoteEndpointAware) {
            server.setRemoteEndpoint(launcher.remoteEndpoint)
        }
        launcher.remoteProxy.showMessage(MessageParams(MessageType.Info, "Connected to Minecraft"))
        connections.add(launcher.startListening())
    }
}