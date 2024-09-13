package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

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
    fun stat(params: UriParams): CompletableFuture<FileStat>

    @JsonRequest
    fun readDirectory(params: UriParams): CompletableFuture<Array<ReadDirectoryResultEntry>>

    @JsonNotification
    fun createDirectory(params: UriParams)

    @JsonRequest
    fun readFile(params: UriParams): CompletableFuture<ReadFileResult>

    @JsonNotification
    fun writeFile(params: WriteFileParams)

    @JsonNotification
    fun delete(params: DeleteParams)

    @JsonNotification
    fun rename(params: RenameParams)
}