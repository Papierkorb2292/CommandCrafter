package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import java.util.concurrent.CompletableFuture

@JsonSegment("scoreboardStorageFileSystem")
interface ScoreboardStorageFileSystem {

    fun setOnDidChangeFileCallback(callback: (Array<FileEvent>) -> Unit)

    @JsonNotification
    fun watch(params: FileSystemWatchParams)

    @JsonNotification
    fun removeWatch(params: FileSystemRemoveWatchParams)

    @JsonRequest
    fun stat(params: UriParams): CompletableFuture<FileSystemResult<FileStat>>

    @JsonRequest
    fun readDirectory(params: UriParams): CompletableFuture<FileSystemResult<Array<ReadDirectoryResultEntry>>>

    @JsonRequest
    fun createDirectory(params: UriParams): CompletableFuture<FileSystemResult<Unit>>

    @JsonRequest
    fun readFile(params: UriParams): CompletableFuture<FileSystemResult<ReadFileResult>>

    @JsonRequest
    fun writeFile(params: WriteFileParams): CompletableFuture<FileSystemResult<Unit>>

    @JsonRequest
    fun delete(params: DeleteParams): CompletableFuture<FileSystemResult<Unit>>

    @JsonRequest
    fun rename(params: RenameParams): CompletableFuture<FileSystemResult<Unit>>
}