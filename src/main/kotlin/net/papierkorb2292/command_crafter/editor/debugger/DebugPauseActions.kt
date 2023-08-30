package net.papierkorb2292.command_crafter.editor.debugger

import org.eclipse.lsp4j.debug.SteppingGranularity

interface DebugPauseActions {
    fun next(granularity: SteppingGranularity)
    fun stepIn(granularity: SteppingGranularity)
    fun stepOut(granularity: SteppingGranularity)
    fun continue_()
}