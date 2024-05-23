package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager

interface ServerDebugManagerContainer {
    fun `command_crafter$getServerDebugManager`(): ServerDebugManager
}