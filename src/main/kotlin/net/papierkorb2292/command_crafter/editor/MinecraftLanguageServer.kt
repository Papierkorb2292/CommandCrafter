package net.papierkorb2292.command_crafter.editor

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.console.*
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.TokenModifier
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.EditorClientAware
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileEvent
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.RenameParams
import net.papierkorb2292.command_crafter.helper.SizeLimitedCallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.mixin.editor.processing.IdentifierAccessor
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class MinecraftLanguageServer(minecraftServer: MinecraftServerConnection, val minecraftClient: MinecraftClientConnection?, val featureConfig: FeatureConfig)
    : MinecraftServerConnectedLanguageServer, EditorClientAware {
    companion object {
        val analyzers: MutableList<FileAnalyseHandler> = mutableListOf()

        val emptyHoverDefault: CompletableFuture<Hover> = CompletableFuture.completedFuture(Hover(emptyList()))
        val emptyDefinitionDefault: CompletableFuture<Either<List<Location>, List<LocationLink>>> = CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        val emptyCompletionsDefault: CompletableFuture<Either<List<CompletionItem>, CompletionList>> = CompletableFuture.completedFuture(Either.forLeft(emptyList()))

        const val AUTO_RELOAD_DATAPACK_FUNCTIONS_CONFIG_PATH = "autoreload.datapack_functions"
        const val AUTO_RELOAD_DATAPACK_JSON_CONFIG_PATH = "autoreload.datapack_json"
        const val AUTO_RELOAD_RESOURCEPACK_CONFIG_PATH = "autoreload.resourcepack"

        fun addAnalyzer(analyzer: FileAnalyseHandler) {
            analyzers += analyzer
        }

        fun fillDiagnosticsSource(diagnostics: List<Diagnostic>) {
            for(diagnostic in diagnostics) {
                diagnostic.source = "CommandCrafter"
            }
        }

        const val CLIENT_LOG_CHANNEL = "client"
    }

    var minecraftServer = minecraftServer
        private set
    var client: CommandCrafterLanguageClient? = null
        private set
    var clientCapabilities: ClientCapabilities? = null
        private set

    val dynamicRegistryManager get() = minecraftServer.dynamicRegistryManager

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

        scoreboardStorageFileSystem.onChangeServerConnection()

        connectServerConsole()
        analyzeAllFiles()
    }

    private fun connectServerConsole() {
        val client = client ?: return
        val console = minecraftServer.serverLog
        val commandExecutor = minecraftServer.commandExecutor
        serverCommandExecutor = commandExecutor
        if (console != null) {
            val serverChannel = console.name
            client.createChannel(Channel(serverChannel, commandExecutor != null))
            console.addMessageCallback(object : SizeLimitedCallbackLinkedBlockingQueue.Callback<String> {
                override fun onElementAdded(e: String) {
                    client.logMinecraftMessage(ConsoleMessage(serverChannel, e))
                }

                override fun shouldRemoveCallback() = !running
            })
        }
        client.updateChannel(Channel(CLIENT_LOG_CHANNEL, serverCommandExecutor != null))
    }

    private fun analyzeAllFiles() {
        for (file in openFiles.values) {
            file.analyzingResult = null
            file.analyzeFile(this)
        }
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        clientCapabilities = params.capabilities
        return CompletableFuture.completedFuture(InitializeResult(ServerCapabilities().apply {
            setTextDocumentSync(TextDocumentSyncOptions().apply {
                change = TextDocumentSyncKind.Incremental
                openClose = true
                hoverProvider = Either.forLeft(true)
                definitionProvider = Either.forLeft(true)
                save = Either.forLeft(true)
                completionProvider = CompletionOptions().apply {
                    resolveProvider = true
                }
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
        PreLaunchLogListener.addLogListener(object : SizeLimitedCallbackLinkedBlockingQueue.Callback<String> {
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
                    it.analyzeFile(this@MinecraftLanguageServer)
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
                file.analyzeFile(this@MinecraftLanguageServer)
            }

            override fun didClose(params: DidCloseTextDocumentParams?) {
                if(params == null) return
                openFiles.remove(params.textDocument.uri)?.run {
                    analyzingResult = null
                }
            }

            private val shaderFileTypes = setOf(
                PackContentFileType.CORE_SHADERS_FILE_TYPE,
                PackContentFileType.POST_SHADERS_FILE_TYPE,
                PackContentFileType.INCLUDE_SHADERS_FILE_TYPE,
                PackContentFileType.POST_EFFECTS_FILE_TYPE
            )

            override fun didSave(params: DidSaveTextDocumentParams) {
                // If enabled: Automatically reload files
                val file = openFiles[params.textDocument.uri] ?: return
                val packContentFileType = PackContentFileType.parsePath(file.parsedUri.path)?.type ?: return
                when(packContentFileType.packType) {
                    PackContentFileType.PackType.DATA -> {
                        val configPath =
                            if(packContentFileType == PackContentFileType.FUNCTIONS_FILE_TYPE)
                                AUTO_RELOAD_DATAPACK_FUNCTIONS_CONFIG_PATH
                            else
                                AUTO_RELOAD_DATAPACK_JSON_CONFIG_PATH
                        if(!featureConfig.isEnabled(configPath, false))
                            return
                        minecraftServer.datapackReloader?.invoke()
                    }
                    PackContentFileType.PackType.RESOURCE -> {
                        if(!featureConfig.isEnabled(AUTO_RELOAD_RESOURCEPACK_CONFIG_PATH, false))
                            return
                        minecraftClient?.reloadResources(ReloadResourcesParams(onlyShaders = packContentFileType in shaderFileTypes))
                    }
                }
            }

            override fun completion(position: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
                val file = openFiles[position.textDocument.uri] ?: return emptyCompletionsDefault
                val analyzer = file.analyzeFile(this@MinecraftLanguageServer) ?: return emptyCompletionsDefault

                val cursor = AnalyzingResult.getCursorFromPosition(file.lines.map { it.toString() }, position.position)
                return analyzer.thenCompose { analyzingResult ->
                    val provider = analyzingResult.getCompletionProviderForCursor(cursor) ?: return@thenCompose emptyCompletionsDefault
                    provider.dataProvider(cursor).thenApply {
                        Either.forLeft(
                            if(clientCapabilities!!.textDocument.completion.completionItem.insertReplaceSupport) it
                            else it.map { completionItem ->
                                completionItem.textEdit = Either.forLeft(completionItem.textEdit.map(
                                    { textEdit -> textEdit },
                                    { insertReplaceEdit ->
                                        TextEdit(
                                            insertReplaceEdit.insert,
                                            insertReplaceEdit.newText
                                        )
                                    }
                                ))
                                completionItem
                            }
                        )
                    }
                }
            }

            override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
                return CompletableFuture.completedFuture(unresolved)
            }

            override fun semanticTokensFull(params: SemanticTokensParams?): CompletableFuture<SemanticTokens> {
                if(params == null) return CompletableFuture.completedFuture(SemanticTokens())
                val file = openFiles[params.textDocument.uri]
                    ?: return CompletableFuture.completedFuture(SemanticTokens())

                val analyzer = file.analyzeFile(this@MinecraftLanguageServer)
                    ?: return CompletableFuture.completedFuture(SemanticTokens())
                return analyzer.thenApply { it.semanticTokens.build() }
            }

            override fun diagnostic(params: DocumentDiagnosticParams?): CompletableFuture<DocumentDiagnosticReport> {
                if(params == null) return CompletableFuture.completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport()))
                val file = openFiles[params.textDocument.uri]
                    ?: return CompletableFuture.completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport()))

                val analyzer = file.analyzeFile(this@MinecraftLanguageServer)
                    ?: return CompletableFuture.completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport()))
                return analyzer.thenApply {
                    fillDiagnosticsSource(it.diagnostics)
                    DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(it.diagnostics))
                }
            }



            override fun hover(params: HoverParams): CompletableFuture<Hover> {
                val file = openFiles[params.textDocument.uri] ?: return emptyHoverDefault
                val analyzer = file.analyzeFile(this@MinecraftLanguageServer) ?: return emptyHoverDefault

                val cursor = AnalyzingResult.getCursorFromPosition(file.lines.map { it.toString() }, params.position)
                return analyzer.thenCompose {
                    val provider = it.getHoverProviderForCursor(cursor) ?: return@thenCompose emptyHoverDefault
                    provider.dataProvider(cursor)
                }
            }

            override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
                val file = openFiles[params.textDocument.uri] ?: return emptyDefinitionDefault
                val analyzer = file.analyzeFile(this@MinecraftLanguageServer) ?: return emptyDefinitionDefault

                val cursor = AnalyzingResult.getCursorFromPosition(file.lines.map { it.toString() }, params.position)
                return analyzer.thenCompose {
                    val provider = it.getDefinitionProviderForCursor(cursor) ?: return@thenCompose emptyDefinitionDefault
                    provider.dataProvider(cursor)
                }
            }
        }
    }

    @JsonDelegate
    fun getScoreboardStorageFileSystem() = scoreboardStorageFileSystem

    private val scoreboardStorageFileSystem = object : ScoreboardStorageFileSystem {
        private val NO_SERVER_SUPPORT_ERROR: FileSystemResult<Nothing> = FileSystemResult(FileNotFoundError("Server does not support scoreboard storage file system"))

        private val watches: Int2ObjectMap<FileSystemWatchParams> = Int2ObjectArrayMap()
        private var onDidChangeFileCallback: ((Array<FileEvent>) -> Unit)? = null

        private var delegateFileSystem: ScoreboardStorageFileSystem? = minecraftServer.createScoreboardStorageFileSystem()

        override fun setOnDidChangeFileCallback(callback: (Array<FileEvent>) -> Unit) {
            onDidChangeFileCallback = callback
            delegateFileSystem?.setOnDidChangeFileCallback(callback)
        }

        override fun watch(params: FileSystemWatchParams) {
            watches[params.watcherId] = params
            delegateFileSystem?.watch(params)
        }

        override fun removeWatch(params: FileSystemRemoveWatchParams) {
            watches.remove(params.watcherId)
            delegateFileSystem?.removeWatch(params)
        }

        override fun stat(params: UriParams): CompletableFuture<FileSystemResult<FileStat>> {
            return delegateFileSystem?.stat(params)
                ?: CompletableFuture.completedFuture(NO_SERVER_SUPPORT_ERROR)
        }

        override fun readDirectory(params: UriParams): CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>> {
            return delegateFileSystem?.readDirectory(params)
                ?: CompletableFuture.completedFuture(NO_SERVER_SUPPORT_ERROR)
        }

        override fun createDirectory(params: UriParams): CompletableFuture<FileSystemResult<Unit>> {
            return delegateFileSystem?.createDirectory(params)
                ?: CompletableFuture.completedFuture(NO_SERVER_SUPPORT_ERROR)
        }

        override fun readFile(params: UriParams): CompletableFuture<FileSystemResult<ReadFileResult>> {
            return delegateFileSystem?.readFile(params)
                ?: CompletableFuture.completedFuture(NO_SERVER_SUPPORT_ERROR)
        }

        override fun writeFile(params: WriteFileParams): CompletableFuture<FileSystemResult<Unit>> {
            return delegateFileSystem?.writeFile(params)
                ?: CompletableFuture.completedFuture(NO_SERVER_SUPPORT_ERROR)
        }

        override fun delete(params: DeleteParams): CompletableFuture<FileSystemResult<Unit>> {
            return delegateFileSystem?.delete(params)
                ?: CompletableFuture.completedFuture(NO_SERVER_SUPPORT_ERROR)
        }

        override fun rename(params: RenameParams): CompletableFuture<FileSystemResult<Unit>> {
            return delegateFileSystem?.rename(params)
                ?: CompletableFuture.completedFuture(NO_SERVER_SUPPORT_ERROR)
        }

        fun onChangeServerConnection() {
            val fileSystem = this@MinecraftLanguageServer.minecraftServer.createScoreboardStorageFileSystem()
            delegateFileSystem = fileSystem
            val onDidChangeFileCallback = onDidChangeFileCallback
            if(onDidChangeFileCallback != null) {
                if(!watches.isEmpty()) {
                    // Technically a file system should send events for all deleted and created files according
                    // to the watches, but the current way requires significantly less computation (and I'm lazy)
                    onDidChangeFileCallback(arrayOf(
                        FileEvent("scoreboardStorage:///", FileChangeType.Deleted),
                        FileEvent("scoreboardStorage:///", FileChangeType.Created)
                    ))
                }
                fileSystem?.setOnDidChangeFileCallback(onDidChangeFileCallback)
            }
            if(fileSystem != null) {
                for(watch in watches.values) {
                    fileSystem.watch(watch)
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

    override fun setTrace(params: SetTraceParams) { }

    @JsonNotification
    fun runCommand(message: ConsoleCommand) {
        val channel = message.channel
        val serverCommandExecutor = serverCommandExecutor
        val serverConsole = minecraftServer.serverLog
        if(serverCommandExecutor != null
            && (channel == CLIENT_LOG_CHANNEL || serverConsole != null && channel == serverConsole.name)) {
            serverCommandExecutor.executeCommand(message.command)
        }
    }

    @JsonNotification
    fun reloadResources(params: ReloadResourcesParams) {
        val client = client ?: return
        val minecraftClient = minecraftClient
        if(minecraftClient == null) {
            client.showMessage(
                MessageParams(
                    MessageType.Error,
                    "Can't reload resources, not connected to Minecraft client"
                )
            )
            return
        }
        minecraftClient.reloadResources(params)
    }

    override fun connect(client: CommandCrafterLanguageClient) {
        this.client = client
        scoreboardStorageFileSystem.setOnDidChangeFileCallback {
            client.onDidChangeScoreboardStorage(CommandCrafterLanguageClient.OnDidChangeScoreboardStorageParams(it))
        }
    }

    fun getOpenFile(uri: String) = openFiles[uri]

    override fun onClosed() {
        running = false
    }

    fun markDocumentation(documentation: String): CompletableFuture<String> {
        val client = client ?: return CompletableFuture.completedFuture(documentation)
        val reader = StringReader(documentation)
        val replacements = mutableListOf<CompletableFuture<Pair<StringRange, String?>>>()
        while(reader.canRead()) {
            val i = reader.cursor
            val c = reader.read()
            if(c == '@') {
                while(reader.canRead() && reader.peek() != ' ' && reader.peek() != '\n')
                    reader.skip()
                val end = reader.cursor
                replacements += CompletableFuture.completedFuture(StringRange(i, end) to "**${reader.string.subSequence(i, end)}**")
                continue
            }
            if(c == ':') {
                val idStart = documentation.subSequence(0, i)
                    .indexOfLast { !IdentifierAccessor.callIsNamespaceCharacterValid(it) } + 1
                while(reader.canRead() && Identifier.isPathCharacterValid(reader.peek()))
                    reader.skip()
                val idEnd = reader.cursor
                val resourceSearchKeywords = PackContentFileType.parseKeywords(reader.string, idStart, idEnd).toSet()
                val range = StringRange(idStart, idEnd)
                replacements += PackContentFileType.findWorkspaceResourceFromId(
                    Identifier.of(documentation.substring(idStart, idEnd)),
                    client,
                    resourceSearchKeywords
                ).thenApply { resource ->
                    range to
                            if(resource == null) "`${range.get(reader.string)}`"
                            else "[${range.get(reader.string)}](${resource.second})"
                }
            }
        }
        return CompletableFuture.allOf(*replacements.toTypedArray()).thenApply {
            val newDocumentation = StringBuilder(documentation)
            for(replacement in replacements) {
                val (range, replacementContent) = replacement.get()
                if(replacementContent == null) continue
                val offset = newDocumentation.length - documentation.length
                newDocumentation.replace(range.start + offset, range.end + offset, replacementContent)
            }
            newDocumentation.toString()
        }
    }

    fun hoverDocumentation(analyzingResult: AnalyzingResult, fileRange: Range): CompletableFuture<Hover> {
        return hoverDocumentation(analyzingResult.documentation, fileRange)
    }
    fun hoverDocumentation(documentation: String?, fileRange: Range): CompletableFuture<Hover> {
        if(documentation == null)
            return CompletableFuture.completedFuture(
                Hover(emptyList(), fileRange)
            )
        return markDocumentation(documentation).thenApply {
            Hover(MarkupContent("markdown", it), fileRange)
        }
    }

    fun findFileAndAnalyze(id: Identifier, packContentFileType: PackContentFileType): CompletableFuture<out AnalyzingResult?> {
        val client = client ?: return CompletableFuture.completedFuture(null)
        return PackContentFileType.findWorkspaceResourceFromIdAndPackContentFileType(
            id,
            packContentFileType,
            client
        ).thenCompose { fileUri ->
            if (fileUri == null) {
                return@thenCompose CompletableFuture.completedFuture(null)
            }
            analyzeFileFromUri(fileUri)
        }
    }

    fun findFileAndGetDocs(id: Identifier, packContentFileType: PackContentFileType): CompletableFuture<String?> {
        return findFileAndAnalyze(id, packContentFileType).thenApply {
            it?.documentation
        }
    }

    fun findFileAndAnalyze(id: Identifier, packContentKeywords: Set<String>): CompletableFuture<out AnalyzingResult?> {
        val client = client ?: return CompletableFuture.completedFuture(null)
        return PackContentFileType.findWorkspaceResourceFromId(
            id,
            client,
            packContentKeywords
        ).thenCompose { fileUri ->
            if (fileUri == null) {
                return@thenCompose CompletableFuture.completedFuture(null)
            }
            analyzeFileFromUri(fileUri.second)
        }
    }

    fun findFileAndGetDocs(id: Identifier, packContentKeywords: Set<String>): CompletableFuture<String?> {
        return findFileAndAnalyze(id, packContentKeywords).thenApply {
            it?.documentation
        }
    }

    fun analyzeFileFromUri(uri: String): CompletableFuture<out AnalyzingResult?> {
        val client = client ?: return CompletableFuture.completedFuture(null)
        val openFile = openFiles[uri]
        return if (openFile != null) {
            openFile.analyzeFile(this) ?: CompletableFuture.completedFuture(null)
        } else {
            client.getFileContent(uri).thenCompose { content ->
                OpenFile.fromString(uri, content, 0).analyzeFile(this) ?: CompletableFuture.completedFuture(null)
            }
        }
    }
}