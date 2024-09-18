package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import java.util.concurrent.CompletableFuture

@JsonSegment("scoreboardStorageFileSystem")
interface ScoreboardStorageFileSystem {

    @JsonNotification
    fun watch(params: FileSystemWatchParams)

    @JsonNotification
    fun removeWatch(params: FileSystemRemoveWatchParams)

    @JsonRequest
    fun stat(params: UriParams): CompletableFuture<FileSystemResult<FileStat>>

    @JsonRequest
    fun readDirectory(params: UriParams): CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>>

    @JsonRequest
    fun createDirectory(params: UriParams): CompletableFuture<FileSystemResult<Void>>

    @JsonRequest
    fun readFile(params: UriParams): CompletableFuture<FileSystemResult<ReadFileResult>>

    @JsonRequest
    fun writeFile(params: WriteFileParams): CompletableFuture<FileSystemResult<Void>>

    @JsonRequest
    fun delete(params: DeleteParams): CompletableFuture<FileSystemResult<Void>>

    @JsonRequest
    fun rename(params: RenameParams): CompletableFuture<FileSystemResult<Void>>
}

class FileNotFoundError(val fileNotFoundErrorMessage: String)
typealias FileSystemResult<TReturnType> = Either<FileNotFoundError, out TReturnType>
