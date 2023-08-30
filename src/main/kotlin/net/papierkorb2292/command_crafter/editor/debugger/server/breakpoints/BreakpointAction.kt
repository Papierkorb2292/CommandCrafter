package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

class BreakpointAction<Location>(
    val location: Location,
    val condition: BreakpointCondition?
)