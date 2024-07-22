package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.Scope

interface IdentifiedVariablesReferencer {
    fun getVariablesReferencerId(): Int
}

fun IdentifiedVariablesReferencer.createScope(name: String) = Scope().also {
    it.name = name
    it.variablesReference = getVariablesReferencerId()
    if(this is CountedVariablesReferencer) {
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }
}