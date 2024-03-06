package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import java.util.concurrent.CompletableFuture

interface EditorDebugConnection {
    fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer)
    fun pauseEnded()
    fun isPaused(): Boolean
    fun updateReloadedBreakpoint(update: BreakpointEventArguments)
    fun reserveBreakpointIds(count: Int): CompletableFuture<ReservedBreakpointIdStart>
    fun popStackFrames(stackFrames: Int)
    fun pushStackFrames(stackFrames: List<MinecraftStackFrame>)
    fun onPauseLocationSkipped()

}

typealias ReservedBreakpointIdStart = Int
