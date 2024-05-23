package net.papierkorb2292.command_crafter.editor.debugger.helper

import java.util.concurrent.CompletableFuture

interface ExecutionCompletedFutureProvider {
    fun `command_crafter$getExecutionCompletedFuture`(): CompletableFuture<Unit>
}