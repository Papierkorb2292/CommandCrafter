package net.papierkorb2292.command_crafter.editor.debugger.server.functions

interface CommandFeedbackConsumer {
    fun onCommandFeedback(feedback: String)
    fun onCommandError(error: String)
}