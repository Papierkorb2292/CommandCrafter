package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.processing.TokenModifier
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class MinecraftLanguageServer(val minecraftServer: MinecraftServerConnection) : LanguageServer, LanguageClientAware, RemoteEndpointAware {
    companion object {
        val analyzers: MutableList<FileAnalyseHandler> = mutableListOf()

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
                openFiles[textDocument.uri] = OpenFile(textDocument.uri, textDocument.text, textDocument.version).also {
                    analyzeFile(it)
                }
            }

            override fun didChange(params: DidChangeTextDocumentParams?) {
                if(params == null) return
                val file = openFiles[params.textDocument.uri] ?: return
                file.analyzingResult = null
                file.version = params.textDocument.version
                for(change in params.contentChanges) {
                    file.applyContentChange(change)
                }
                analyzeFile(file)
            }

            fun analyzeFile(file: OpenFile): CompletableFuture<AnalyzingResult>? {
                val runningAnalyzer = file.analyzingResult
                if(runningAnalyzer != null)
                    return runningAnalyzer
                for(analyzer in analyzers) {
                    if(analyzer.canHandle(file)) {
                        val version = file.version
                        return CompletableFuture.supplyAsync {
                            analyzer.analyze(file, minecraftServer)
                        }.apply {
                            file.analyzingResult = this
                            thenAccept {
                                if(file.version != version)
                                    return@thenAccept
                                client?.publishDiagnostics(PublishDiagnosticsParams(file.uri, it.diagnostics, version))
                            }
                        }
                    }
                }
                return null
            }

            override fun didClose(params: DidCloseTextDocumentParams?) {
                if(params == null) return
                openFiles.remove(params.textDocument.uri)?.run {
                    analyzingResult = null
                }
            }

            override fun didSave(params: DidSaveTextDocumentParams?) {

            }

            override fun semanticTokensFull(params: SemanticTokensParams?): CompletableFuture<SemanticTokens> {
                if(params == null) return CompletableFuture.completedFuture(SemanticTokens())
                val file = openFiles[params.textDocument.uri]
                    ?: return CompletableFuture.completedFuture(SemanticTokens())

                val analyzer = analyzeFile(file)
                    ?: return CompletableFuture.completedFuture(SemanticTokens())
                return analyzer.thenApply { it.semanticTokens.build() }
            }

            override fun diagnostic(params: DocumentDiagnosticParams?): CompletableFuture<DocumentDiagnosticReport> {
                if(params == null) return CompletableFuture.completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport()))
                val file = openFiles[params.textDocument.uri]
                    ?: return CompletableFuture.completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport()))

                val analyzer = analyzeFile(file)
                    ?: return CompletableFuture.completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport()))
                return analyzer.thenApply { DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(it.diagnostics)) }
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

}