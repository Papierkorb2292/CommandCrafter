package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.papierkorb2292.command_crafter.editor.debugger.helper.Positionable
import org.eclipse.lsp4j.debug.SourceBreakpoint

class PositionableBreakpoint(val sourceBreakpoint: SourceBreakpoint): Positionable {
    override val line get() = sourceBreakpoint.line
    override val char: Int? get() = sourceBreakpoint.column
    override fun setPos(line: Int, char: Int?) {
        sourceBreakpoint.line = line
        sourceBreakpoint.column = char
    }
}