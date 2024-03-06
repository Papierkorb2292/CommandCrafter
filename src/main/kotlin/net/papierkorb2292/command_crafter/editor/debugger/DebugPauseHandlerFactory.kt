package net.papierkorb2292.command_crafter.editor.debugger

import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

interface DebugPauseHandlerFactory<in TDebugFrame : PauseContext.DebugFrame> {
    fun createDebugPauseHandler(debugFrame: TDebugFrame): DebugPauseHandler
}