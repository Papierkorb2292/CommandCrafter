package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.console.Channel
import net.papierkorb2292.command_crafter.editor.console.ConsoleMessage
import net.papierkorb2292.command_crafter.editor.console.RemoveChannelNotification
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileEvent
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

interface CommandCrafterLanguageClient : LanguageClient, EditorFileSystemAccess {
    @JsonNotification
    fun createChannel(channel: Channel)

    @JsonNotification
    fun removeChannel(channel: RemoveChannelNotification)

    @JsonNotification
    fun updateChannel(channel: Channel)

    @JsonNotification
    fun logMinecraftMessage(message: ConsoleMessage)

    @JsonRequest
    override fun getFileContent(uri: String): CompletableFuture<String>

    @JsonRequest
    override fun findFiles(pattern: String): CompletableFuture<Array<String>>

    @JsonRequest
    override fun findFilesRelative(params: FindFilesRelativeParams): CompletableFuture<Array<String>>

    @JsonRequest
    override fun fileExists(uri: String): CompletableFuture<Boolean>

    @JsonNotification("scoreboardStorageFileSystem/onDidChangeFile")
    fun onDidChangeScoreboardStorage(params: OnDidChangeScoreboardStorageParams)

    class OnDidChangeScoreboardStorageParams(val events: Array<FileEvent>)
}