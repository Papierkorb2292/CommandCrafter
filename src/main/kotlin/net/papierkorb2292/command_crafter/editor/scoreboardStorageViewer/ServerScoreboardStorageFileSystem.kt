package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer

import com.google.common.io.ByteStreams
import com.google.gson.Gson
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.minecraft.nbt.NbtIo
import net.minecraft.scoreboard.ScoreHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.papierkorb2292.command_crafter.editor.EditorURI
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.*
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

class ServerScoreboardStorageFileSystem(val server: MinecraftServer) : ScoreboardStorageFileSystem {
    companion object {
        private const val SCOREBOARDS_DIRECTORY = "scoreboards"
        private const val STORAGES_DIRECTORY = "storages"
        private val GSON = Gson()
    }

    private val watches: Int2ObjectMap<Watch> = Int2ObjectArrayMap()
    private var lastFileCacheId: Pair<Directory, String>? = null
    private var lastFileCacheContent: ByteArray? = null

    private var onDidChangeFileCallback: ((Array<FileEvent>) -> Unit)? = null

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
        if(resolvedPathResult.isLeft)
            return CompletableFuture.completedFuture(Either.forLeft(resolvedPathResult.left))
        val resolvedPath = resolvedPathResult.right
        if(resolvedPath.fileName == null)
            return CompletableFuture.completedFuture(Either.forLeft(FileNotFoundError("stat can only be called on files")))
        val contentResult = getFileContent(resolvedPath.directory, resolvedPath.fileName)
        if(contentResult.isLeft)
            return CompletableFuture.completedFuture(Either.forLeft(contentResult.left))
        val contentSize = contentResult.right.size
        return CompletableFuture.completedFuture(Either.forRight(FileStat(FileType.FILE, 0, Util.getEpochTimeMs().toInt(), contentSize)))
    }

    override fun readDirectory(params: UriParams): CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>> {
        val resolvedPathResult = resolveUri(params.uri)
        if(resolvedPathResult.isLeft)
            return CompletableFuture.completedFuture(Either.forLeft(resolvedPathResult.left))
        val resolvedPath = resolvedPathResult.right
        if(resolvedPath.fileName != null)
            return CompletableFuture.completedFuture(Either.forLeft(FileNotFoundError("readDirectory can only be called on directories")))

        val entries = when(resolvedPath.directory) {
            Directory.ROOT -> arrayOf(
                ReadDirectoryResultEntry(SCOREBOARDS_DIRECTORY, FileType.DIRECTORY),
                ReadDirectoryResultEntry(STORAGES_DIRECTORY, FileType.DIRECTORY)
            )
            Directory.SCOREBOARDS -> server.scoreboard.objectives.map {
                ReadDirectoryResultEntry(it.name, FileType.FILE)
            }.toTypedArray()
            Directory.STORAGES -> server.dataCommandStorage.ids.map {
                ReadDirectoryResultEntry(it.toString(), FileType.FILE)
            }.toArray(::arrayOfNulls)
        }
        return CompletableFuture.completedFuture(Either.forRight(entries))
    }

    override fun createDirectory(params: UriParams): CompletableFuture<FileSystemResult<Void?>> {
        // Directories can't be created
        return CompletableFuture.completedFuture(Either.forRight(null))
    }

    override fun readFile(params: UriParams): CompletableFuture<FileSystemResult<ReadFileResult>> {
        val resolvedPathResult = resolveUri(params.uri)
        if(resolvedPathResult.isLeft)
            return CompletableFuture.completedFuture(Either.forLeft(resolvedPathResult.left))
        val resolvedPath = resolvedPathResult.right
        if(resolvedPath.fileName == null)
            return CompletableFuture.completedFuture(Either.forLeft(FileNotFoundError("readFile can only be called on files")))
        val contentResult = getFileContent(resolvedPath.directory, resolvedPath.fileName)
        if(contentResult.isLeft)
            return CompletableFuture.completedFuture(Either.forLeft(contentResult.left))
        val content = contentResult.right
        val base64Content = Base64.getEncoder().encode(content).decodeToString()
        return CompletableFuture.completedFuture(Either.forRight(ReadFileResult(base64Content)))
    }

    override fun writeFile(params: WriteFileParams): CompletableFuture<FileSystemResult<Void?>> {
        val resolvedPathResult = resolveUri(params.uri)
        if(resolvedPathResult.isLeft)
            return CompletableFuture.completedFuture(Either.forLeft(resolvedPathResult.left))
        val resolvedPath = resolvedPathResult.right
        if(resolvedPath.fileName == null)
            return CompletableFuture.completedFuture(Either.forLeft(FileNotFoundError("writeFile can only be called on files")))
        val content = Base64.getDecoder().decode(params.contentBase64)
        //TODO IMPORTANT: Update data synchronously
        val result = when(resolvedPath.directory) {
            Directory.SCOREBOARDS -> updateScoreboardData(resolvedPath, content)
            Directory.STORAGES -> updateStorageData(resolvedPath, content)
            else -> Either.forLeft(FileNotFoundError("No files outside of scoreboard/storage directories"))
        }
        if(result.isRight && lastFileCacheId == Pair(resolvedPath.directory, resolvedPath.fileName))
            lastFileCacheId = null
        return CompletableFuture.completedFuture(result)
    }

    private fun updateScoreboardData(resolvedPath: ResolvedPath, content: ByteArray): FileSystemResult<Void?> {
        val jsonRoot = try {
            GSON.fromJson(content.decodeToString(), JsonObject::class.java)
        } catch(e: Exception) {
            return Either.forRight(null)
        }
        val objective = server.scoreboard.getNullableObjective(resolvedPath.fileName)
            ?: return Either.forLeft(FileNotFoundError("Objective ${resolvedPath.fileName} not found"))
        if(objective.criterion.isReadOnly)
            return Either.forRight(null)
        for((owner, value) in jsonRoot.entrySet()) {
            if(!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber)
                continue
            server.scoreboard.getOrCreateScore(ScoreHolder.fromName(owner), objective).score = value.asInt
        }
        for(entry in server.scoreboard.getScoreboardEntries(objective)) {
            if(!jsonRoot.has(entry.owner))
                server.scoreboard.removeScore(ScoreHolder.fromName(entry.owner), objective)
        }
        return Either.forRight(null)
    }

    private fun updateStorageData(resolvedPath: ResolvedPath, content: ByteArray): FileSystemResult<Void?> {
        val id = Identifier.tryParse(resolvedPath.fileName)
            ?: return Either.forLeft(FileNotFoundError("Storage ${resolvedPath.fileName} not found"))
        val nbtCompound = try {
            NbtIo.readCompound(ByteStreams.newDataInput(content))
        } catch(e: Exception) {
            return Either.forLeft(FileNotFoundError("Invalid NBT content"))
        }
        server.dataCommandStorage.set(id, nbtCompound)
        return Either.forRight(null)
    }

    override fun delete(params: DeleteParams): CompletableFuture<FileSystemResult<Void?>> {
        // Deleting files isn't implemented
        return CompletableFuture.completedFuture(Either.forRight(null))
    }

    override fun rename(params: RenameParams): CompletableFuture<FileSystemResult<Void?>> {
        // Renaming files isn't implemented
        return CompletableFuture.completedFuture(Either.forRight(null))
    }

    private fun resolveUri(uri: String): FileSystemResult<ResolvedPath> {
        val path = EditorURI.parseURI(uri).path
        if(path.isEmpty())
            return Either.forRight(ResolvedPath(Directory.ROOT, null))
        val parts = path.split('/')
        if(parts.size == 1) {
            return when(parts[0]) {
                SCOREBOARDS_DIRECTORY -> Either.forRight(ResolvedPath(Directory.SCOREBOARDS, null))
                STORAGES_DIRECTORY -> Either.forRight(ResolvedPath(Directory.STORAGES, null))
                else -> Either.forLeft(FileNotFoundError("No files outside of scoreboard/storage directories"))
            }
        }
        if(parts.size != 2)
            return Either.forLeft(FileNotFoundError("No files outside of scoreboard/storage directories"))
        val directory = when(parts[0]) {
            SCOREBOARDS_DIRECTORY -> Directory.SCOREBOARDS
            STORAGES_DIRECTORY -> Directory.STORAGES
            else -> return Either.forLeft(FileNotFoundError("No directories besides scoreboards and storages"))
        }
        val fileName = parts[1]
        return Either.forRight(ResolvedPath(directory, if(fileName.isEmpty()) null else fileName))
    }

    private fun getFileContent(directory: Directory, fileName: String): FileSystemResult<ByteArray> {
        val fileId = Pair(directory, fileName)
        if(fileId == lastFileCacheId)
            return Either.forRight(lastFileCacheContent!!)
        val content: FileSystemResult<ByteArray> = when(directory) {
            Directory.SCOREBOARDS -> {
                val objective = server.scoreboard.getNullableObjective(fileName)
                    ?: return Either.forLeft(FileNotFoundError("Objective $fileName not found"))
                val jsonRoot = JsonObject()
                for(entry in server.scoreboard.getScoreboardEntries(objective)) {
                    jsonRoot.addProperty(entry.owner, entry.value)
                }
                Either.forRight(jsonRoot.toString().encodeToByteArray())
            }
            Directory.STORAGES -> {
                val id = Identifier.tryParse(fileName)
                    ?: return Either.forLeft(FileNotFoundError("Storage $fileName not found"))
                val nbtCompound = server.dataCommandStorage.get(id)
                    ?: return Either.forLeft(FileNotFoundError("Storage $fileName not found"))
                val dataOutput = ByteStreams.newDataOutput()
                NbtIo.writeCompound(nbtCompound, dataOutput)
                Either.forRight(dataOutput.toByteArray())
            }
            else -> return Either.forLeft(FileNotFoundError("No files outside of scoreboard/storage directories"))
        }
        lastFileCacheId = fileId
        lastFileCacheContent = content.right
        return content
    }

    private fun onFileUpdate(directory: Directory, fileName: String, updateType: FileChangeType) {
        val callback = onDidChangeFileCallback ?: return
        val fileUri = "scoreboardStorage:///${directory.toFolderName()}/$fileName"
        for(watch in watches.values) {
            if(watch.matches(fileUri))
                callback.invoke(arrayOf(FileEvent(fileUri, updateType)))
        }
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
            if(!uriPattern.matcher(uri).matches())
                return false
            return excludeUriPatterns.all { !it.matcher(uri).matches() }
        }
    }
}