package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

interface DebugInformationContainer<TBreakpointLocation, TDebugFrame : PauseContext.DebugFrame> {
    fun `command_crafter$getDebugInformation`(): DebugInformation<TBreakpointLocation, TDebugFrame>?
    fun `command_crafter$setDebugInformation`(debugInformation: DebugInformation<TBreakpointLocation, TDebugFrame>)
}