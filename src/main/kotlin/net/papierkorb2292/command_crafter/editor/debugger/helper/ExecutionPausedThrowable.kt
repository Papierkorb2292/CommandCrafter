package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

interface ExecutionPausedThrowable {
    val wrapperConsumer: PauseContext.ExecutionWrapperConsumer
}