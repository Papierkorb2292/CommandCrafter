package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.StepInTargetsManager
import org.eclipse.lsp4j.debug.StepInTarget
import org.eclipse.lsp4j.debug.StepInTargetsResponse
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.concurrent.CompletableFuture

class FunctionTagDebugPauseHandler(val debugFrame: FunctionTagDebugFrame) : DebugPauseHandler {
    override fun findNextPauseLocation() {
        debugFrame.pauseOnEntryIndex(debugFrame.currentEntryIndex + 1)
    }

    override fun getStackFrames(): List<MinecraftStackFrame> {
        TODO("Not yet implemented")
    }

    override fun onExitFrame() { }

    override fun next(granularity: SteppingGranularity) {
        debugFrame.pauseOnEntryIndex(debugFrame.currentEntryIndex + 1)
    }

    override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
        debugFrame.pauseContext.stepIntoFrame()
    }

    override fun stepOut(granularity: SteppingGranularity) {
        debugFrame.pauseContext.pauseAfterExitFrame()
    }

    override fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse> =
        CompletableFuture.completedFuture(StepInTargetsResponse().apply {
            targets = arrayOf(StepInTarget().apply {
                id = debugFrame.pauseContext.stepInTargetsManager.addStepInTarget(StepInTargetsManager.Target {
                    debugFrame.pauseContext.stepIntoFrame()
                })
                val currentFunctionId = debugFrame.pauseContext.server.commandFunctionManager
                    .getTag(debugFrame.tagId)!!
                    .elementAt(debugFrame.currentEntryIndex)
                label = "Step into function '$currentFunctionId'"
            })
        })

    override fun continue_() {
        debugFrame.pauseContext.removePause()
    }
}