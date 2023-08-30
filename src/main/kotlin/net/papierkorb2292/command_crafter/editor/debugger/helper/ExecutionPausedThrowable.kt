package net.papierkorb2292.command_crafter.editor.debugger.helper

import java.util.concurrent.CompletableFuture

class ExecutionPausedThrowable(val functionCompletion: CompletableFuture<Int>) : Throwable("The function execution was paused and no result is available yet")