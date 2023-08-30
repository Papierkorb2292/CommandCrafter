package net.papierkorb2292.command_crafter.editor.debugger

interface DebugPauseHandlerFactory<in TPauseContext> {
    fun createDebugPauseHandler(pauseContext: TPauseContext): DebugPauseHandler
}