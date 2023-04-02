package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class MinecraftLanguageServer(commandCrafter: CommandCrafter) : LanguageServer, LanguageClientAware, RemoteEndpointAware {

    private var client: LanguageClient? = null
    private var remote: RemoteEndpoint? = null

    private val openFiles: MutableMap<String, OpenFile> = HashMap()

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
                if(params == null) return
                val textDocument = params.textDocument
                openFiles[textDocument.uri] = OpenFile(textDocument.uri, textDocument.text)
            }

            override fun didChange(params: DidChangeTextDocumentParams?) {
                if(params == null) return
                val file = openFiles[params.textDocument.uri] ?: return
                for(change in params.contentChanges) {
                    file.applyContentChange(change)
                }
            }

            override fun didClose(params: DidCloseTextDocumentParams?) {
                if(params == null) return
                openFiles.remove(params.textDocument.uri)
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