package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer

import com.google.common.collect.Lists
import com.google.common.io.ByteStreams
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonWriter
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registries
import net.minecraft.scoreboard.ScoreHolder
import net.minecraft.scoreboard.ScoreboardCriterion
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.util.dynamic.Codecs
import net.papierkorb2292.command_crafter.editor.EditorURI
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import net.papierkorb2292.command_crafter.helper.getOrNull
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
        private val GSON = Gson()

        private val DATA_UPDATE_QUEUE = mutableListOf<() -> Unit>()

        private var lastFileUpdateTimeMs = 0L

        val createdFileSystems: MutableMap<ServerPlayNetworkHandler, MutableMap<UUID, ServerScoreboardStorageFileSystem>> = mutableMapOf()

        private val CRITERION_CODEC = object : Codec<ScoreboardCriterion> {
            override fun <T : Any?> encode(input: ScoreboardCriterion, ops: DynamicOps<T>, prefix: T)
                = DataResult.success(ops.createString(input.name))

            override fun <T: Any> decode(ops: DynamicOps<T>, input: T): DataResult<com.mojang.datafixers.util.Pair<ScoreboardCriterion, T>> {
                @Suppress("UNCHECKED_CAST")
                val analyzingOps = (StringRangeTree.AnalyzingDynamicOps.CURRENT_ANALYZING_OPS.getOrNull() as StringRangeTree.AnalyzingDynamicOps<T>?)
                if(analyzingOps != null) {
                    val criterionSuggestionsList = ScoreboardCriterion.getAllSimpleCriteria()
                        .mapTo(mutableListOf()) {
                            StringRangeTree.Suggestion(ops.createString(it))
                        }

                    for(statType in Registries.STAT_TYPE) {
                        forEachStatName(statType) {
                            criterionSuggestionsList += StringRangeTree.Suggestion(ops.createString(it))
                        }
                    }

                    analyzingOps.getNodeStartSuggestions(input) += criterionSuggestionsList
                }
                return ops.getStringValue(input).flatMap {  criterionName ->
                    ScoreboardCriterion.getOrCreateStatCriterion(criterionName).map {
                        DataResult.success(com.mojang.datafixers.util.Pair(it, ops.empty()))
                    }.orElse(DataResult.error { "Unknown criterion '$criterionName'" })
                }
            }

            private inline fun <T> forEachStatName(statType: StatType<T>, action: (String) -> Unit) {
                for(entry in statType.registry) {
                    val name = Stat.getName(statType, entry)
                    action(name)
                }
            }
        }

        private val OBJECTIVE_DATA_CODEC: Codec<ObjectiveData> =
            RecordCodecBuilder.create {
                it.group(
                    CRITERION_CODEC.fieldOf("criterion").forGetter(ObjectiveData::criterion)
                ).apply(it, ::ObjectiveData)
            }

        private val ENTRIES_CODEC: Codec<Map<String, Int>> = Codecs.strictUnboundedMap(Codec.STRING, Codec.INT)

        val OBJECTIVE_CODEC: Codec<ObjectiveFile> =
            RecordCodecBuilder.create {
                it.group(
                    ENTRIES_CODEC.fieldOf("entries").forGetter(ObjectiveFile::scores),
                    OBJECTIVE_DATA_CODEC.fieldOf("objective").forGetter(ObjectiveFile::objectiveData)
                ).apply(it, ::ObjectiveFile)
            }


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

        val objectiveFile = try {
            val json = GSON.fromJson(content.decodeToString(), JsonObject::class.java)
            OBJECTIVE_CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
        } catch(e: Exception) {
            onFileUpdate(Directory.SCOREBOARDS, objective.name, FileChangeType.Changed)
            return FileSystemResult(Unit)
        }

        if(objective.criterion.isReadOnly) {
            onFileUpdate(Directory.SCOREBOARDS, objective.name, FileChangeType.Changed)
            return FileSystemResult(Unit)
        }
        for((owner, value) in objectiveFile.scores.entries) {
            server.scoreboard.getOrCreateScore(ScoreHolder.fromName(owner), objective).score = value
        }
        for(entry in server.scoreboard.getScoreboardEntries(objective)) {
            if(!objectiveFile.scores.containsKey(entry.owner))
                server.scoreboard.removeScore(ScoreHolder.fromName(entry.owner), objective)
        }

        if(objective.criterion != objectiveFile.objectiveData.criterion) {
            val objectivesByCriterion = (server.scoreboard as ScoreboardAccessor).objectivesByCriterion
            objectivesByCriterion[objective.criterion]?.remove(objective)
            objectivesByCriterion.computeIfAbsent(objectiveFile.objectiveData.criterion, Function { Lists.newArrayList() }).add(objective)
            (objective as ScoreboardObjectiveAccessor).setCriterion(objectiveFile.objectiveData.criterion)
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
                val scoresMap = LinkedHashMap<String, Int>()
                for(entry in server.scoreboard.getScoreboardEntries(objective).sortedBy { it.owner }) {
                    scoresMap[entry.owner] = entry.value
                }
                val objectiveData = ObjectiveData(objective.criterion)
                val objectiveFile = ObjectiveFile(scoresMap, objectiveData)
                val json = OBJECTIVE_CODEC.encodeStart(JsonOps.INSTANCE, objectiveFile).orThrow
                val stringWriter = StringWriter()
                val jsonWriter = JsonWriter(stringWriter)
                jsonWriter.setIndent("  ")
                GSON.toJson(json, jsonWriter)
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

    class Watch(private val uriPattern: Pattern, private val excludeUriPatterns: List<Pattern>) {
        fun matches(uri: String): Boolean {
            if(!uriPattern.matcher(uri).find())
                return false
            return excludeUriPatterns.all { !it.matcher(uri).find() }
        }
    }

    data class FileUpdate(val directory: Directory, val objectName: String, val updateType: FileChangeType)

    class ObjectiveData(val criterion: ScoreboardCriterion)
    class ObjectiveFile(val scores: Map<String, Int>, val objectiveData: ObjectiveData)
}