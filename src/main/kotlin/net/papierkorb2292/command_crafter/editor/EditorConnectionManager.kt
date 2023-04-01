package net.papierkorb2292.command_crafter.editor

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class EditorConnectionManager {

    fun startServer(connectionAcceptor: EditorConnectionAcceptor, serverSupplier: Supplier<LanguageServer>) {
        CompletableFuture.runAsync {
            while(connectionAcceptor.isRunning()) {
                val editorConnection = connectionAcceptor.accept()
                handleConnection(editorConnection, serverSupplier.get())
            }
        }
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
        launcher.startListening()
    }
}