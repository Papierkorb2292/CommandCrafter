package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

interface BreakpointConditionParser {
    fun parseCondition(condition: String?, hitCondition: String?): BreakpointCondition
}