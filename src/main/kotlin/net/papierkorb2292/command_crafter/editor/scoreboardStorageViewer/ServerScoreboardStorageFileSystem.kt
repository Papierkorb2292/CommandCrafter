package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer

import com.google.common.collect.Lists
import com.google.common.io.ByteStreams
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.StringNbtReader
import net.minecraft.scoreboard.ScoreHolder
import net.minecraft.scoreboard.ScoreboardCriterion
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.papierkorb2292.command_crafter.editor.EditorURI
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer.ScoreboardAccessor
import net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer.ScoreboardObjectiveAccessor
import java.io.StringWriter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.regex.Pattern
import java.util.stream.Stream

class ServerScoreboardStorageFileSystem(val server: MinecraftServer) : ScoreboardStorageFileSystem {
    companion object {
        private const val SCOREBOARDS_DIRECTORY = "scoreboards"
        private const val STORAGES_DIRECTORY = "storages"
        private const val SCOREBOARDS_JSON_ENTRIES_NAME = "entries"
        private const val SCOREBOARDS_JSON_OBJECTIVE_DATA_NAME = "objective"
        private const val SCOREBOARDS_JSON_CRITERIA_NAME = "criteria"
        private val GSON = Gson()

        private val DATA_UPDATE_QUEUE = mutableListOf<() -> Unit>()

        private var lastFileUpdateTimeMs = 0L

        val createdFileSystems: MutableMap<ServerPlayNetworkHandler, MutableMap<UUID, ServerScoreboardStorageFileSystem>> = mutableMapOf()

        fun runUpdates() {
            synchronized(DATA_UPDATE_QUEUE) {
                for(update in DATA_UPDATE_QUEUE)
                    update()
                DATA_UPDATE_QUEUE.clear()
            }

            val timeMs = Util.getEpochTimeMs()
            if(timeMs - lastFileUpdateTimeMs < 2000)
                return
            lastFileUpdateTimeMs = timeMs
            for(playerFileSystems in createdFileSystems.values) {
                for(fileSystem in playerFileSystems.values) {
                    fileSystem.flushQueuedFileUpdates()
                }
            }
        }

        fun onFileUpdate(directory: Directory, objectName: String, updateType: FileChangeType) {
            for(playerFileSystems in createdFileSystems.values) {
                for(fileSystem in playerFileSystems.values) {
                    fileSystem.onFileUpdate(directory, objectName, updateType)
                }
            }
        }

        fun queueDataUpdate(update: () -> Unit) {
            synchronized(DATA_UPDATE_QUEUE) {
                DATA_UPDATE_QUEUE += update
            }
        }

        fun registerTickUpdateRunner() {
            ServerTickEvents.END_SERVER_TICK.register {
                runUpdates()
            }
        }
    }

    private val watches: Int2ObjectMap<Watch> = Int2ObjectArrayMap()
    private var lastFileCacheId: Pair<Directory, String>? = null
    private var lastFileCacheContent: ByteArray? = null

    private var onDidChangeFileCallback: ((Array<FileEvent>) -> Unit)? = null

    // This is a set because the same file can be updated multiple times in a single tick
    private val queuedFileUpdates = mutableSetOf<FileUpdate>()

    override fun setOnDidChangeFileCallback(callback: (Array<FileEvent>) -> Unit) {
        onDidChangeFileCallback = callback
    }

    override fun watch(params: FileSystemWatchParams) {
        val uriRegex = EditorURI.parseURI(params.uri).toPatternMatch()
        val uriPattern = Pattern.compile(if(params.recursive) "^$uriRegex" else "^$uriRegex$")
        val excludePatterns = params.excludes.map {
            Pattern.compile('^' + EditorURI.parseURI(it).toPatternMatch())
        }
        watches[params.watcherId] = Watch(
            uriPattern,
            excludePatterns
        )
    }

    override fun removeWatch(params: FileSystemRemoveWatchParams) {
        watches.remove(params.watcherId)
    }

    override fun stat(params: UriParams): CompletableFuture<FileSystemResult<FileStat>> {
        val resolvedPathResult = resolveUri(params.uri)
        val resolvedPath = resolvedPathResult.handleErrorAndGetResult {
            return CompletableFuture.completedFuture(it)
        }
        if(resolvedPath.fileName == null)
            return CompletableFuture.completedFuture(FileSystemResult(FileStat(FileType.DIRECTORY, 0, Util.getEpochTimeMs().toInt(), 1)))
        val contentResult = getFileContent(resolvedPath.directory, resolvedPath.fileName)
        val content = contentResult.handleErrorAndGetResult {
            return CompletableFuture.completedFuture(it)
        }
        val contentSize = content.size
        return CompletableFuture.completedFuture(FileSystemResult(FileStat(FileType.FILE, 0, Util.getEpochTimeMs().toInt(), contentSize)))
    }

    override fun readDirectory(params: UriParams): CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>> {
        val resolvedPathResult = resolveUri(params.uri)
        val resolvedPath = resolvedPathResult.handleErrorAndGetResult {
            return CompletableFuture.completedFuture(it)
        }
        if(resolvedPath.fileName != null)
            return CompletableFuture.completedFuture(FileSystemResult(FileNotFoundError("readDirectory can only be called on directories")))

        val entries = when(resolvedPath.directory) {
            Directory.ROOT -> arrayOf(
                ReadDirectoryResultEntry(SCOREBOARDS_DIRECTORY, FileType.DIRECTORY),
                ReadDirectoryResultEntry(STORAGES_DIRECTORY, FileType.DIRECTORY)
            )
            Directory.SCOREBOARDS -> server.scoreboard.objectives.sortedBy { it.name }.map {
                ReadDirectoryResultEntry(createUrl(Directory.SCOREBOARDS, it.name, ".json"), FileType.FILE)
            }.toTypedArray()
            Directory.STORAGES -> server.dataCommandStorage.ids.sorted { id1, id2 ->
                val namespaceCmp = id1.namespace.compareTo(id2.namespace)
                if(namespaceCmp != 0)
                    return@sorted namespaceCmp
                id1.path.compareTo(id2.path)
            }.flatMap {
                Stream.of(
                    ReadDirectoryResultEntry(createUrl(Directory.STORAGES, it.toString(), ".nbt"), FileType.FILE),
                    ReadDirectoryResultEntry(createUrl(Directory.STORAGES, it.toString(), ".snbt"), FileType.FILE)
                )
            }.toList().toTypedArray()
        }
        return CompletableFuture.completedFuture(FileSystemResult(entries))
    }

    override fun createDirectory(params: UriParams): CompletableFuture<FileSystemResult<Unit>> {
        // Directories can't be created
        return CompletableFuture.completedFuture(FileSystemResult(Unit))
    }

    override fun readFile(params: UriParams): CompletableFuture<FileSystemResult<ReadFileResult>> {
        val resolvedPathResult = resolveUri(params.uri)
        val resolvedPath = resolvedPathResult.handleErrorAndGetResult {
            return CompletableFuture.completedFuture(it)
        }
        if(resolvedPath.fileName == null)
            return CompletableFuture.completedFuture(FileSystemResult(FileNotFoundError("readFile can only be called on files")))
        val contentResult = getFileContent(resolvedPath.directory, resolvedPath.fileName)
        val content = contentResult.handleErrorAndGetResult {
            return CompletableFuture.completedFuture(it)
        }
        val base64Content = Base64.getEncoder().encode(content).decodeToString()
        return CompletableFuture.completedFuture(FileSystemResult(ReadFileResult(base64Content)))
    }

    override fun writeFile(params: WriteFileParams): CompletableFuture<FileSystemResult<Unit>> {
        val resolvedPathResult = resolveUri(params.uri)
        val resolvedPath = resolvedPathResult.handleErrorAndGetResult {
            return CompletableFuture.completedFuture(it)
        }
        if(resolvedPath.fileName == null)
            return CompletableFuture.completedFuture(FileSystemResult(FileNotFoundError("writeFile can only be called on files")))
        val content = Base64.getDecoder().decode(params.contentBase64)
        val future = CompletableFuture<FileSystemResult<Unit>>()
        queueDataUpdate {
            val result = when(resolvedPath.directory) {
                Directory.SCOREBOARDS -> updateScoreboardData(resolvedPath, content)
                Directory.STORAGES -> updateStorageData(resolvedPath, content)
                else -> FileSystemResult(FileNotFoundError("No files outside of scoreboard/storage directories"))
            }
            if(result.type == FileSystemResult.ResultType.SUCCESS && lastFileCacheId == Pair(resolvedPath.directory, resolvedPath.fileName))
                lastFileCacheId = null
            future.complete(result)
        }
        return future
    }

    private fun updateScoreboardData(resolvedPath: ResolvedPath, content: ByteArray): FileSystemResult<Unit> {
        if(!resolvedPath.fileName!!.endsWith(".json"))
            return FileSystemResult(FileNotFoundError("Only JSON files in scoreboards directory"))
        val objective = server.scoreboard.getNullableObjective(resolvedPath.fileName.substring(0, resolvedPath.fileName.length - 5))
            ?: return FileSystemResult(FileNotFoundError("Objective ${resolvedPath.fileName} not found"))

        val jsonRoot = try {
            GSON.fromJson(content.decodeToString(), JsonObject::class.java)
        } catch(e: Exception) {
            return FileSystemResult(Unit)
        }
        val entries = jsonRoot.get(SCOREBOARDS_JSON_ENTRIES_NAME)
        if(entries !is JsonObject)
            return FileSystemResult(Unit)
        val objectiveData = jsonRoot.get(SCOREBOARDS_JSON_OBJECTIVE_DATA_NAME)
        if(objectiveData !is JsonObject)
            return FileSystemResult(Unit)

        val criterionJson = objectiveData.get(SCOREBOARDS_JSON_CRITERIA_NAME)
        val parsedCriterion =
            if(criterionJson !is JsonPrimitive || !criterionJson.isString)
                objective.criterion
            else
                ScoreboardCriterion.getOrCreateStatCriterion(criterionJson.asString).orElse(objective.criterion)
        if(objective.criterion.isReadOnly)
            return FileSystemResult(Unit)
        for((owner, value) in entries.entrySet()) {
            if(!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber)
                continue
            server.scoreboard.getOrCreateScore(ScoreHolder.fromName(owner), objective).score = value.asInt
        }
        for(entry in server.scoreboard.getScoreboardEntries(objective)) {
            if(!entries.has(entry.owner))
                server.scoreboard.removeScore(ScoreHolder.fromName(entry.owner), objective)
        }

        if(objective.criterion != parsedCriterion) {
            val objectivesByCriterion = (server.scoreboard as ScoreboardAccessor).objectivesByCriterion
            objectivesByCriterion[objective.criterion]?.remove(objective)
            objectivesByCriterion.computeIfAbsent(parsedCriterion, Function { Lists.newArrayList() }).add(objective)
            (objective as ScoreboardObjectiveAccessor).setCriterion(parsedCriterion)
        }
        return FileSystemResult(Unit)
    }

    private fun updateStorageData(resolvedPath: ResolvedPath, content: ByteArray): FileSystemResult<Unit> {
        val isNbt = resolvedPath.fileName!!.endsWith(".nbt")
        if(!isNbt && !resolvedPath.fileName.endsWith(".snbt"))
            return FileSystemResult(FileNotFoundError("Only NBT/SNBT files in storages directory"))
        val id = Identifier.tryParse(resolvedPath.fileName.substring(0, resolvedPath.fileName.length - if(isNbt) 4 else 5))
            ?: return FileSystemResult(FileNotFoundError("Storage ${resolvedPath.fileName} not found"))
        val nbtCompound = try {
            if(!isNbt)
                StringNbtReader.parse(content.decodeToString())
            else
                NbtIo.readCompound(ByteStreams.newDataInput(content))
        } catch(e: Exception) {
            return FileSystemResult(FileNotFoundError("Invalid NBT content"))
        }
        server.dataCommandStorage.set(id, nbtCompound)
        return FileSystemResult(Unit)
    }

    override fun delete(params: DeleteParams): CompletableFuture<FileSystemResult<Unit>> {
        // Deleting files isn't implemented
        return CompletableFuture.completedFuture(FileSystemResult(Unit))
    }

    override fun rename(params: RenameParams): CompletableFuture<FileSystemResult<Unit>> {
        // Renaming files isn't implemented
        return CompletableFuture.completedFuture(FileSystemResult(Unit))
    }

    private fun resolveUri(uri: String): FileSystemResult<ResolvedPath> {
        val path = EditorURI.parseURI(uri).path.removePrefix("/")
        if(path.isEmpty())
            return FileSystemResult(ResolvedPath(Directory.ROOT, null))
        val parts = path.split('/')
        if(parts.size == 1) {
            return when(parts[0]) {
                SCOREBOARDS_DIRECTORY -> FileSystemResult(ResolvedPath(Directory.SCOREBOARDS, null))
                STORAGES_DIRECTORY -> FileSystemResult(ResolvedPath(Directory.STORAGES, null))
                else -> FileSystemResult(FileNotFoundError("No files outside of scoreboard/storage directories"))
            }
        }
        if(parts.size != 2)
            return FileSystemResult(FileNotFoundError("No files outside of scoreboard/storage directories"))
        val directory = when(parts[0]) {
            SCOREBOARDS_DIRECTORY -> Directory.SCOREBOARDS
            STORAGES_DIRECTORY -> Directory.STORAGES
            else -> return FileSystemResult(FileNotFoundError("No directories besides scoreboards and storages"))
        }
        val fileName = parts[1]
        return FileSystemResult(ResolvedPath(directory, if(fileName.isEmpty()) null else fileName))
    }

    private fun createUrl(directory: Directory, fileName: String, fileExtension: String): String {
        return "scoreboardStorage:///${directory.toFolderName()}/$fileName$fileExtension"
    }

    private fun getFileContent(directory: Directory, fileName: String): FileSystemResult<ByteArray> {
        val fileId = Pair(directory, fileName)
        if(fileId == lastFileCacheId)
            return FileSystemResult(lastFileCacheContent!!)
        val content: FileSystemResult<ByteArray> = when(directory) {
            Directory.SCOREBOARDS -> {
                if(!fileName.endsWith(".json"))
                    return FileSystemResult(FileNotFoundError("Only JSON files in scoreboards directory"))
                val objective = server.scoreboard.getNullableObjective(fileName.substring(0, fileName.length - 5))
                    ?: return FileSystemResult(FileNotFoundError("Objective $fileName not found"))
                val jsonRoot = JsonObject()
                val entriesObject = JsonObject()
                for(entry in server.scoreboard.getScoreboardEntries(objective).sortedBy { it.owner }) {
                    entriesObject.addProperty(entry.owner, entry.value)
                }
                jsonRoot.add(SCOREBOARDS_JSON_ENTRIES_NAME, entriesObject)
                val objectiveData = JsonObject()
                objectiveData.addProperty(SCOREBOARDS_JSON_CRITERIA_NAME, objective.criterion.name)
                jsonRoot.add(SCOREBOARDS_JSON_OBJECTIVE_DATA_NAME, objectiveData)
                val stringWriter = StringWriter()
                val jsonWriter = JsonWriter(stringWriter)
                jsonWriter.setIndent("  ")
                GSON.toJson(jsonRoot, jsonWriter)
                FileSystemResult(stringWriter.toString().encodeToByteArray())
            }
            Directory.STORAGES -> {
                val isNbt = fileName.endsWith(".nbt")
                if(!isNbt && !fileName.endsWith(".snbt"))
                    return FileSystemResult(FileNotFoundError("Only NBT/SNBT files in storages directory"))
                val id = Identifier.tryParse(fileName.substring(0, fileName.length - if(isNbt) 4 else 5))
                    ?: return FileSystemResult(FileNotFoundError("Storage $fileName not found"))
                val nbtCompound = server.dataCommandStorage.get(id)
                    ?: return FileSystemResult(FileNotFoundError("Storage $fileName not found"))
                if(!isNbt) {
                    val snbt = nbtCompound.toString()
                    FileSystemResult(snbt.encodeToByteArray())
                } else {
                    val dataOutput = ByteStreams.newDataOutput()
                    NbtIo.writeCompound(nbtCompound, dataOutput)
                    FileSystemResult(dataOutput.toByteArray())
                }
            }
            else -> return FileSystemResult(FileNotFoundError("No files outside of scoreboard/storage directories"))
        }
        if(content.type == FileSystemResult.ResultType.SUCCESS) {
            lastFileCacheId = fileId
            lastFileCacheContent = content.result!!
        }
        return content
    }

    fun onFileUpdate(directory: Directory, objectName: String, updateType: FileChangeType) {
        queuedFileUpdates += FileUpdate(directory, objectName, updateType)
    }

    fun flushQueuedFileUpdates() {
        if(queuedFileUpdates.isEmpty())
            return
        val lastFileCacheId = lastFileCacheId
        for(update in queuedFileUpdates) {
            if(lastFileCacheId?.first == Directory.SCOREBOARDS
                && update.directory == Directory.SCOREBOARDS
                && lastFileCacheId.second == update.objectName + ".json"
                ) {
                this.lastFileCacheId = null
                break
            }
            if(lastFileCacheId?.first == Directory.STORAGES
                && update.directory == Directory.STORAGES
                && (lastFileCacheId.second == update.objectName + ".nbt" || lastFileCacheId.second == update.objectName + ".snbt")
                ) {
                this.lastFileCacheId = null
                break
            }
        }
        val onDidChangeFileCallback = onDidChangeFileCallback
        if(onDidChangeFileCallback == null) {
            queuedFileUpdates.clear()
            return
        }

        val fileEvents = queuedFileUpdates.flatMap {
            val fileUris = if(it.directory == Directory.SCOREBOARDS) {
                arrayOf(createUrl(Directory.SCOREBOARDS, it.objectName, ".json"))
            } else {
                arrayOf(
                    createUrl(Directory.STORAGES, it.objectName, ".nbt"),
                    createUrl(Directory.STORAGES, it.objectName, ".snbt")
                )
            }

            fileUris.mapNotNull { uri ->
                if(watches.values.any { watch -> watch.matches(uri) })
                    FileEvent(uri, it.updateType)
                else null
            }
        }

        onDidChangeFileCallback.invoke(fileEvents.toTypedArray())
        queuedFileUpdates.clear()
    }

    enum class Directory {
        ROOT,
        SCOREBOARDS,
        STORAGES;

        fun toFolderName(): String {
            return when(this) {
                ROOT -> ""
                SCOREBOARDS -> SCOREBOARDS_DIRECTORY
                STORAGES -> STORAGES_DIRECTORY
            }
        }
    }

    class ResolvedPath(val directory: Directory, val fileName: String?)

    /**
     * Represents a watch on a file system
     * @param params The parameters given by the language client
     * @param path The resolved path of the file to watch. Null if the path is invalid
     */
    class Watch(private val uriPattern: Pattern, private val excludeUriPatterns: List<Pattern>) {
        fun matches(uri: String): Boolean {
            if(!uriPattern.matcher(uri).find())
                return false
            return excludeUriPatterns.all { !it.matcher(uri).find() }
        }
    }

    data class FileUpdate(val directory: Directory, val objectName: String, val updateType: FileChangeType)
}