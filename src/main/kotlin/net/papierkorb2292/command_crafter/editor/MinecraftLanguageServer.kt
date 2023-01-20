package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class MinecraftLanguageServer(commandCrafter: CommandCrafter) : LanguageServer, LanguageClientAware {

    private var client: LanguageClient? = null

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        return CompletableFuture.completedFuture(InitializeResult(ServerCapabilities(), ServerInfo()))
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
}