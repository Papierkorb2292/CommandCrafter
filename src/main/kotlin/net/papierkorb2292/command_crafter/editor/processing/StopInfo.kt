package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.workspace.WorkspaceAnalyzingScheduler
import java.util.concurrent.CompletableFuture

data class StopInfo(val completableFuture: CompletableFuture<*>, val taskTimer: WorkspaceAnalyzingScheduler.TaskTimer?) {
    fun shouldStop() = completableFuture.isDone || (taskTimer != null && taskTimer.shouldStop())
}