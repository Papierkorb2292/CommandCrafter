package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class VariablesReferenceMapper : VariablesReferencer {
    private val variablesReferences = mutableListOf<VariablesReferencer>()

    /**
     * Registers a variable referencer that can be accessed by methods
     * inherited from [VariablesReferencer]. Returns the id of the
     * variable reference, that can be sent to the editor and used to
     * access the registered variable referencer.
     * It is important that the returned id is a positive number.
     */
    fun addVariablesReferencer(referencer: VariablesReferencer): Int {
        val id = variablesReferences.size + 1
        variablesReferences.add(referencer)
        return id
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        val variablesReferenceIndex = args.variablesReference - 1
        val variablesReferences = variablesReferences
        if(variablesReferenceIndex >= variablesReferences.size)
            return CompletableFuture.completedFuture(emptyArray())
        return variablesReferences[variablesReferenceIndex].getVariables(args)
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val variablesReferenceIndex = args.variablesReference - 1
        val variablesReferences = variablesReferences
        if(variablesReferenceIndex >= variablesReferences.size)
            return CompletableFuture.completedFuture(null)
        return variablesReferences[variablesReferenceIndex].setVariable(args)
    }

}