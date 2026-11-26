package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import java.util.function.Supplier

interface CommandFeedbackConsumer {
    fun onCommandFeedback(feedback: Supplier<String>)
    fun onCommandError(error: Supplier<String>)
}