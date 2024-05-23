package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

class CommandExecutionPausedThrowable(override val wrapperConsumer: PauseContext.ExecutionWrapperConsumer) : Throwable("The function execution was paused"), ExecutionPausedThrowable