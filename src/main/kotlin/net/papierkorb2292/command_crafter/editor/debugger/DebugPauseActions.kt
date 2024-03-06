package net.papierkorb2292.command_crafter.editor.debugger

import org.eclipse.lsp4j.debug.StepInTargetsResponse
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.concurrent.CompletableFuture

interface DebugPauseActions {
    fun next(granularity: SteppingGranularity)
    fun stepIn(granularity: SteppingGranularity, targetId: Int? = null)
    fun stepOut(granularity: SteppingGranularity)
    fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse>
    fun continue_()
}