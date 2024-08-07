package net.papierkorb2292.command_crafter.editor

import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

interface CommandCrafterDebugClient : IDebugProtocolClient, EditorClientFileFinder {

    @JsonRequest
    override fun findFiles(pattern: String): CompletableFuture<Array<String>>

    @JsonRequest
    fun getWorkspaceRoot(): CompletableFuture<String>
}