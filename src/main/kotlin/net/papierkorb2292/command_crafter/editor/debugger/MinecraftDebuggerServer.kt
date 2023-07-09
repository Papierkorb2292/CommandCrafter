package net.papierkorb2292.command_crafter.editor.debugger

import net.papierkorb2292.command_crafter.editor.EditorService
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.TerminateArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.util.concurrent.CompletableFuture

class MinecraftDebuggerServer(private var minecraftServer: MinecraftServerConnection) : IDebugProtocolServer, EditorService {

    private var client: IDebugProtocolClient? = null

    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        return CompletableFuture.completedFuture(Capabilities())
    }

    override fun launch(args: MutableMap<String, Any>?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun disconnect(args: DisconnectArguments): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun terminate(args: TerminateArguments): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun setMinecraftServerConnection(connection: MinecraftServerConnection) {
        minecraftServer = connection
    }

    fun connect(client: IDebugProtocolClient) {
        this.client = client
    }
}