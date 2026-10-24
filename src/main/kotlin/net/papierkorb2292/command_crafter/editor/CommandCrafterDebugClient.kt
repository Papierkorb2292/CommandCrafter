package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.files.FileStat
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

interface CommandCrafterDebugClient : IDebugProtocolClient, EditorClientFileFinder {

    @JsonRequest
    override fun findFiles(pattern: String): CompletableFuture<Array<String>>

    @JsonRequest
    override fun findFilesRelative(params: FindFilesRelativeParams): CompletableFuture<Array<String>>

    @JsonRequest
    override fun statFile(uri: String): CompletableFuture<FileStat?>

    @Deprecated("This has issues with multi-root workspaces and it is no longer necessary")
    fun getWorkspaceRoot(): CompletableFuture<String>
}