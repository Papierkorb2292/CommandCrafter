package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

interface PauseContextContainer {
    fun `command_crafter$getPauseContext`(): PauseContext?
    fun `command_crafter$setPauseContext`(pauseContext: PauseContext)
}
