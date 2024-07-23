package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser

interface IdentifiedBreakpointParserProvider<TBreakpointParser: BreakpointParser<TBreakpointLocation>, TBreakpointLocation> {
    fun `command_crafter$getBreakpointParser`(id: Identifier): TBreakpointParser?
}