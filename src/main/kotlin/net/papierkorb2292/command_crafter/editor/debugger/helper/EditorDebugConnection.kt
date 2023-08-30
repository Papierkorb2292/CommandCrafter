package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.StoppedEventArguments

interface EditorDebugConnection {
    fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer)
    fun pauseEnded()
    fun isPaused(): Boolean
    fun updateReloadedBreakpoint(breakpoint: Breakpoint)
    fun popStackFrames(stackFrames: Int)
    fun pushStackFrames(stackFrames: List<MinecraftStackFrame>)
}
