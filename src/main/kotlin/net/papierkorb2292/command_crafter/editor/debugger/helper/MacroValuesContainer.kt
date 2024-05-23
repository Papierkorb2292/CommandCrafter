package net.papierkorb2292.command_crafter.editor.debugger.helper

interface MacroValuesContainer {
    fun `command_crafter$setMacroNames`(macroNames: List<String>)
    fun `command_crafter$setMacroValues`(macroValues: List<String>)
    fun `command_crafter$getMacroNames`(): List<String>?
    fun `command_crafter$getMacroValues`(): List<String>?
}