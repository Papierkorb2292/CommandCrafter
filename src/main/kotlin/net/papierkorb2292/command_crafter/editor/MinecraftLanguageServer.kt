package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class MinecraftLanguageServer(commandCrafter: CommandCrafter) : LanguageServer, LanguageClientAware, RemoteEndpointAware {

    private var client: LanguageClient? = null
    private var remote: RemoteEndpoint? = null

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        return CompletableFuture.completedFuture(InitializeResult(ServerCapabilities().apply {
            setTextDocumentSync(TextDocumentSyncOptions().apply {
                change = TextDocumentSyncKind.Incremental
                openClose = true
            })
        }, ServerInfo("Minecraft Language Server")))
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {

    }

    override fun getTextDocumentService(): TextDocumentService {
        return object : TextDocumentService {
            override fun didOpen(params: DidOpenTextDocumentParams?) {

            }

            override fun didChange(params: DidChangeTextDocumentParams?) {

            }

            override fun didClose(params: DidCloseTextDocumentParams?) {

            }

            override fun didSave(params: DidSaveTextDocumentParams?) {

            }

        }
    }

    override fun getWorkspaceService(): WorkspaceService {
        return object : WorkspaceService {
            override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {

            }

            override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {

            }

        }
    }

    override fun connect(client: LanguageClient?) {
        this.client = client
    }

    override fun setRemoteEndpoint(remote: RemoteEndpoint) {
        this.remote = remote
    }
}