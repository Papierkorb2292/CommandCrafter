package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

interface IdentifiedDebugInformationProvider<TBreakpointLocation, TDebugFrame : PauseContext.DebugFrame> {
    fun `command_crafter$getDebugInformation`(id: Identifier): DebugInformation<TBreakpointLocation, TDebugFrame>?
}