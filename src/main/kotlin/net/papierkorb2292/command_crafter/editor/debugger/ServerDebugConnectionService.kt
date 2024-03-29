package net.papierkorb2292.command_crafter.editor.debugger

import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceResponse
import java.util.concurrent.CompletableFuture

interface ServerDebugConnectionService {
    fun setBreakpoints(
        breakpoints: Array<UnparsedServerBreakpoint>,
        source: Source,
        fileType: PackContentFileType,
        id: Identifier,
        editorDebugConnection: EditorDebugConnection
    ): CompletableFuture<SetBreakpointsResponse>

    fun retrieveSourceReference(sourceReference: Int, editorDebugConnection: EditorDebugConnection): CompletableFuture<SourceResponse>

    fun removeEditorDebugConnection(editorDebugConnection: EditorDebugConnection)
}
