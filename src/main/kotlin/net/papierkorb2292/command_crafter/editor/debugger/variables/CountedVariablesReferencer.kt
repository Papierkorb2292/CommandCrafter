package net.papierkorb2292.command_crafter.editor.debugger.variables

interface CountedVariablesReferencer : VariablesReferencer {
    val namedVariableCount: Int
    val indexedVariableCount: Int
}