package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.papierkorb2292.command_crafter.editor.debugger.helper.Positionable

class PositionableBreakpoint(val breakpoint: UnparsedServerBreakpoint): Positionable {
    override val line get() = breakpoint.sourceBreakpoint.line
    override val char: Int get() = breakpoint.sourceBreakpoint.column ?: 1
    override fun setPos(line: Int, char: Int) {
        breakpoint.sourceBreakpoint.line = line
        breakpoint.sourceBreakpoint.column = char
    }

    override fun toString() = "PositionableBreakpoint(breakpoint=$breakpoint)"
}