package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.processing.TokenModifier
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class MinecraftLanguageServer(val minecraftServer: MinecraftServerConnection) : LanguageServer, LanguageClientAware, RemoteEndpointAware {
    companion object {
        private val analyzers: MutableList<FileAnalyseHandler> = mutableListOf()

        fun addAnalyzer(analyzer: FileAnalyseHandler) {
            analyzers += analyzer
        }
    }

    private var client: LanguageClient? = null
    private var remote: Endpoint? = null

    private val openFiles: MutableMap<String, OpenFile> = HashMap()

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        return CompletableFuture.completedFuture(InitializeResult(ServerCapabilities().apply {
            setTextDocumentSync(TextDocumentSyncOptions().apply {
                change = TextDocumentSyncKind.Incremental
                openClose = true
            })
            semanticTokensProvider = SemanticTokensWithRegistrationOptions(
                SemanticTokensLegend(TokenType.TYPES, TokenModifier.MODIFIERS),
                true
            )
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

            override fun semanticTokensFull(params: SemanticTokensParams?): CompletableFuture<SemanticTokens> {
                if(params == null) return CompletableFuture.completedFuture(SemanticTokens())

                return CompletableFuture.supplyAsync {
                    val tokens = SemanticTokens()
                    val file = openFiles[params.textDocument.uri]
                        ?: return@supplyAsync tokens

                    for(analyzer in analyzers) {
                        if(analyzer.canHandle(file)) {
                            analyzer.fillSemanticTokens(file, tokens, minecraftServer)
                            break
                        }
                    }
                    return@supplyAsync tokens
                }
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

    override fun setRemoteEndpoint(remote: Endpoint) {
        this.remote = remote
    }

    interface FileAnalyseHandler {
        fun canHandle(file: OpenFile): Boolean
        fun fillSemanticTokens(file: OpenFile, tokens: SemanticTokens, server: MinecraftServerConnection)
    }
}