package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

interface EditorDebugConnection {
    val lifecycle: Lifecycle
    val oneTimeDebugTarget: DebugTarget?
    val nextSourceReference: Int
    val suspendServer: Boolean
    fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer)
    fun pauseEnded()
    fun isPaused(): Boolean
    fun updateReloadedBreakpoint(update: BreakpointEventArguments)
    fun reserveBreakpointIds(count: Int): CompletableFuture<ReservedBreakpointIdStart>
    fun popStackFrames(stackFrames: Int)
    fun pushStackFrames(stackFrames: List<MinecraftStackFrame>)
    fun output(args: OutputEventArguments)
    fun onSourceReferenceAdded()

    class Lifecycle {
        val configurationDoneEvent: CompletableFuture<Void> = CompletableFuture()
        val shouldExitEvent: CompletableFuture<ExitedEventArguments> = CompletableFuture()
    }

    data class DebugTarget(val targetFileType: PackContentFileType, val targetId: Identifier, val stopOnEntry: Boolean)
}

fun EditorDebugConnection.onPauseLocationSkipped() {
    output(OutputEventArguments().apply {
        category = OutputEventArgumentsCategory.IMPORTANT
        output = "Skipped pause location"
    })
}

typealias ReservedBreakpointIdStart = Int
