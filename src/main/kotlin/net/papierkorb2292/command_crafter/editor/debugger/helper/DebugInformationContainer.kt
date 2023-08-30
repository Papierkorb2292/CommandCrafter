package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation

interface DebugInformationContainer<TBreakpointLocation, TPauseContext> {
    fun `command_crafter$getDebugInformation`(): DebugInformation<TBreakpointLocation, TPauseContext>?
    fun `command_crafter$setDebugInformation`(debugInformation: DebugInformation<TBreakpointLocation, TPauseContext>)
}