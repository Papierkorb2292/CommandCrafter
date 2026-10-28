package net.papierkorb2292.command_crafter.editor

import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.papierkorb2292.command_crafter.editor.files.FileStat
import net.papierkorb2292.command_crafter.editor.files.FileType
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.symbols.*
import net.papierkorb2292.command_crafter.helper.DaemonThreadFactory
import org.eclipse.lsp4j.FileChangeType
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

class WorkspaceManager(private val languageServer: MinecraftLanguageServer) {
    companion object {
        private val processingThreadFactory = DaemonThreadFactory("LanguageServerWorkspaceFilesProcessing")
    }

    private val workspaceFiles: MutableMap<String, EditorDocument> = HashMap()
    private val workspacePackInfos: MutableMap<String, WorkspacePackInfo> = HashMap()
    private val workspacePackFolderNames: Object2IntMap<Pair<String, PackContentFileType.PackType>> = Object2IntOpenHashMap()

    val processingExecutor: ExecutorService = Executors.newSingleThreadExecutor(processingThreadFactory)

    fun findDatapackForUri(uri: EditorURI): WorkspacePackInfo? {
        var result: WorkspacePackInfo? = null
        for(packInfo in workspacePackInfos.values) {
            if(!uri.startsWith(packInfo.baseUri)) continue
            // Even if packs are nested (for whatever reason), try to associate packs in a deterministic way (always select the innermost pack)
            if(result == null || packInfo.baseUri.path.length > result.baseUri.path.length)
                result = packInfo
        }
        return result
    }

    fun scanWorkspaceForPacks() {
        val fileAccess = languageServer.getFileAccess() ?: return
        fileAccess.findFiles("**/pack.mcmeta").thenAccept { packMetaFiles ->
            processingExecutor.execute {
                for(packMetaFile in packMetaFiles) {
                    addWorkspacePackInfo(EditorURI.parseURI(packMetaFile))
                }
            }
        }
    }

    fun onWorkspaceFileChange(uri: String, changeType: FileChangeType, createdStats: FileStat?) {
        if(changeType == FileChangeType.Created)
            require(createdStats != null) { "Created file must have metadata" }

        val parsedUri = EditorURI.parseURI(uri)
        val fileName = parsedUri.getFileName()
        if(fileName == "pack.mcmeta") {
            when(changeType) {
                FileChangeType.Created ->
                    if(createdStats!!.type.hasFlag(FileType.FILE))
                        addWorkspacePackInfo(parsedUri)
                FileChangeType.Deleted ->
                    removeWorkspacePackInfo(parsedUri)
                FileChangeType.Changed -> {}
            }
        } else if(fileName == "assets" || fileName == "data") {
            if(createdStats == null || createdStats.type.hasFlag(FileType.DIRECTORY)) {
                val parent = parsedUri.getParent()
                val pack = workspacePackInfos[parent.toString()]
                if(pack != null) {
                    changeWorkspacePackFolderNameCount(pack, -1)
                    pack.updatePackType(languageServer).thenAccept {
                        changeWorkspacePackFolderNameCount(pack, 1)
                        analyzeAllPackFiles(pack)
                    }
                }
            }
        }

        if(changeType == FileChangeType.Deleted) {
            stopTrackingWorkspaceFile(uri)
        } else if(changeType == FileChangeType.Created && createdStats!!.type.hasFlag(FileType.FILE)) {
            startTrackingWorkspaceFile(uri, parsedUri)
        }
    }

    fun updateAllFilePackInfos(openFiles: Collection<EditorDocument>) {
        var anyChanged = false
        for(file in openFiles) {
            val prevPackInfo = file.workspacePackInfo
            val newPackInfo = findDatapackForUri(file.parsedUri)
            if(prevPackInfo == newPackInfo)
                continue
            anyChanged = true
            file.setWorkspacePackInfo(newPackInfo)

            file.stopAnalyzing()
            file.persistentAnalyzerData = null
            file.startAnalyzingFile(languageServer)
        }

        val workspaceFilesIt = workspaceFiles.iterator()
        while(workspaceFilesIt.hasNext()) {
            val (uri, file) = workspaceFilesIt.next()
            val prevPackInfo = file.workspacePackInfo
            val newPackInfo = findDatapackForUri(file.parsedUri)
            if(prevPackInfo == newPackInfo)
                continue

            stopTrackingWorkspaceFile(uri, false)
            anyChanged = true

            if(newPackInfo == null) {
                workspaceFilesIt.remove()
                continue
            }

            file.setWorkspacePackInfo(newPackInfo)

            if(file.typedId == null) {
                workspaceFilesIt.remove()
                continue
            }

            startTrackingWorkspaceFile(uri, file.parsedUri, useExistingFile = true)
        }

        if(anyChanged)
            languageServer.client!!.refreshDiagnostics()
    }

    // Should only be called from the processing executor
    private fun startTrackingWorkspaceFile(uri: String, parsedUri: EditorURI = EditorURI.parseURI(uri), useExistingFile: Boolean = false) {
        val document: EditorDocument
        if(!useExistingFile) {
            val filePackInfo = findDatapackForUri(parsedUri) ?: return
            document = EditorDocument(uri, mutableListOf()).apply {
                setWorkspacePackInfo(filePackInfo)
            }
            val symbolInfo = MinecraftLanguageServer.fileSymbolInfos[document.typedId?.type] ?: return
            val fileExtension = parsedUri.getFileName().substringAfter('.', "")
            if(symbolInfo.fileExtension != fileExtension)
                return

            workspaceFiles[uri] = document
        } else {
            document = workspaceFiles[uri]!!
        }

        val filePackInfo = document.workspacePackInfo!!
        val (id, type) = document.typedId!!
        val symbolInfo = MinecraftLanguageServer.fileSymbolInfos[type]!!

        val position = org.eclipse.lsp4j.Position()
        languageServer.symbolManager.getRegistry(type).getOrCreateSymbol(id).declarations.workspaceLocations[uri] =
            listOf(SymbolLocationMap.SymbolLocation({ org.eclipse.lsp4j.Range(position, position) }, null))

        if(symbolInfo.analyzeMode == SymbolAnalyzeMode.NoAnalyzer)
            return

        fun analyzeContent() {
            // TODO: Analyze
            if(symbolInfo.analyzeMode == SymbolAnalyzeMode.AnalyzerWithDocumentation) {
                // TODO: Add symbol again later if analyzer provides documentation, but only if analyzer wasn't cancelled (file was deleted, packs changed, etc.)
            }
        }

        if(useExistingFile) {
            analyzeContent()
            return
        }

        languageServer.getFileContent(uri).thenAcceptAsync({ content ->
            val currentDocument = workspaceFiles[uri]
            if(currentDocument == null || currentDocument.workspacePackInfo != filePackInfo)
                return@thenAcceptAsync
            val fullDocument = EditorDocument.fromString(uri, content)
            fullDocument.setWorkspacePackInfo(filePackInfo)
            workspaceFiles[uri] = fullDocument
            analyzeContent()
        }, processingExecutor)
    }

    // Should only be called from the processing executor
    private fun stopTrackingWorkspaceFile(uri: String, removeEntry: Boolean = true) {
        val file = if(removeEntry) workspaceFiles.remove(uri) else workspaceFiles[uri]
        val typedId = file?.typedId
        if(typedId != null) {
            val registry = languageServer.symbolManager.getRegistry(typedId.type)
            registry.getSymbolOrNull(typedId.id)?.declarations?.removeWorkspaceLocation(uri)
            registry.tryRemoveEmptySymbol(typedId.id)
        }
    }

    // Should only be called from the processing executor
    private fun addWorkspacePackInfo(packMetaUri: EditorURI) {
        val parent = packMetaUri.getParent()
        val parentString = parent.toString()
        if(workspacePackInfos.containsKey(parentString))
            return
        WorkspacePackInfo.forFolder(parent, languageServer).thenAcceptAsync({ newPackInfo ->
            workspacePackInfos[parentString] = newPackInfo
            changeWorkspacePackFolderNameCount(newPackInfo, 1)
            updateAllFilePackInfos(languageServer.openFiles.values)
            val fileAccess = languageServer.getFileAccess() ?: return@thenAcceptAsync
            val files = fileAccess.findFilesRelative(FindFilesRelativeParams(newPackInfo.baseUri.toString(), "**")).get()

            for(uri in files) {
                val existingFile = workspaceFiles[uri]
                if(existingFile != null) {
                    val newFilePackInfo = findDatapackForUri(existingFile.parsedUri)
                    if(newFilePackInfo == newPackInfo) {
                        stopTrackingWorkspaceFile(uri, false)
                        existingFile.setWorkspacePackInfo(newPackInfo)
                        startTrackingWorkspaceFile(uri, existingFile.parsedUri, true)
                    }
                } else {
                    startTrackingWorkspaceFile(uri)
                }
            }
        }, processingExecutor)
    }

    // Should only be called from the processing executor
    private fun removeWorkspacePackInfo(packMetaUri: EditorURI) {
        val baseUri = packMetaUri.getParent().toString()
        val pack = workspacePackInfos.remove(baseUri)
        if(pack != null) {
            changeWorkspacePackFolderNameCount(pack, -1)
            updateAllFilePackInfos(languageServer.openFiles.values)
        }
    }

    // Should only be called from the processing executor
    private fun changeWorkspacePackFolderNameCount(pack: WorkspacePackInfo, amount: Int) {
        val packType = pack.packType ?: return
        val key = pack.packFolderName to packType
        val count = workspacePackFolderNames.getInt(key) + amount
        if(count > 0)
            workspacePackFolderNames.put(key, count)
        else
            workspacePackFolderNames.removeInt(key)
    }

    private fun analyzeAllPackFiles(pack: WorkspacePackInfo) {
        languageServer.analyzeAllFiles { file -> file.workspacePackInfo == pack }
    }
}
