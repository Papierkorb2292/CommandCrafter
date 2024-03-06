package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.MinecraftLanguageServerExtension
import net.papierkorb2292.command_crafter.editor.console.*
import net.papierkorb2292.command_crafter.editor.processing.TokenModifier
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.EditorClientAware
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.helper.CallbackLinkedBlockingQueue
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class MinecraftLanguageServer(private var minecraftServer: MinecraftServerConnection)
    : MinecraftServerConnectedLanguageServer, EditorClientAware,
    MinecraftLanguageServerExtension {
    companion object {
        val analyzers: MutableList<FileAnalyseHandler> = mutableListOf()

        fun addAnalyzer(analyzer: FileAnalyseHandler) {
            analyzers += analyzer
        }

        const val CLIENT_LOG_CHANNEL = "client"
    }

    private var client: EditorClient? = null
    private var remote: Endpoint? = null
    private var running = true

    private val openFiles: MutableMap<String, OpenFile> = HashMap()

    private var serverCommandExecutor: CommandExecutor? = null

    override fun setMinecraftServerConnection(connection: MinecraftServerConnection) {
        val client = client ?: return
        val prevConsole = minecraftServer.serverLog
        if(prevConsole != null) {
            client.removeChannel(RemoveChannelNotification(prevConsole.name))
        }

        minecraftServer = connection

        connectServerConsole()
    }

    private fun connectServerConsole() {
        val client = client ?: return
        val console = minecraftServer.serverLog
        val commandExecutor = minecraftServer.commandExecutor
        serverCommandExecutor = commandExecutor
        if (console != null) {
            val serverChannel = console.name
            client.createChannel(Channel(serverChannel, commandExecutor != null))
            console.addMessageCallback(object : CallbackLinkedBlockingQueue.Callback<String> {
                override fun onElementAdded(e: String) {
                    client.logMinecraftMessage(ConsoleMessage(serverChannel, e))
                }

                override fun shouldRemoveCallback() = !running
            })
        }
        client.updateChannel(Channel(CLIENT_LOG_CHANNEL, serverCommandExecutor != null))
    }

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

    override fun initialized(params: InitializedParams) {
        val client = client ?: return

        client.createChannel(Channel(CLIENT_LOG_CHANNEL, false))
        PreLaunchLogListener.addLogListener(object : CallbackLinkedBlockingQueue.Callback<String> {
            override fun onElementAdded(e: String) {
                client.logMinecraftMessage(ConsoleMessage(CLIENT_LOG_CHANNEL, e))
            }

            override fun shouldRemoveCallback() = !running
        })

        connectServerConsole()
    }

    override fun shutdown(): CompletableFuture<Any> {
        running = false
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {

    }

    override fun getTextDocumentService(): TextDocumentService {
        return object : TextDocumentService {
            override fun didOpen(params: DidOpenTextDocumentParams?) {
                if(params == null) return
                val textDocument = params.textDocument
                openFiles[textDocument.uri] = OpenFile(textDocument.uri, OpenFile.linesFromString(textDocument.text), textDocument.version).also {
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

    override fun setTrace(params: SetTraceParams) { }

    override fun runCommand(message: ConsoleCommand) {
        val channel = message.channel
        val serverCommandExecutor = serverCommandExecutor
        val serverConsole = minecraftServer.serverLog
        if(serverCommandExecutor != null
            && (channel == CLIENT_LOG_CHANNEL || serverConsole != null && channel == serverConsole.name)) {
            serverCommandExecutor.executeCommand(message.command)
        }
    }

    override fun connect(client: EditorClient) {
        this.client = client
    }
}