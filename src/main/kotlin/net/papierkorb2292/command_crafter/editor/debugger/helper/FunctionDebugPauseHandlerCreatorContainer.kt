package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.server.FunctionDebugPauseHandlerCreator

interface FunctionDebugPauseHandlerCreatorContainer {
    fun `command_crafter$setHandlerCreator`(creator: FunctionDebugPauseHandlerCreator)
    fun `command_crafter$getPauseHandlerCreator`(): FunctionDebugPauseHandlerCreator?
}