package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable

class DoubleValueReference(
    private var double: Double?,
    private val doubleSetter: (Double?) -> Double?
): VariableValueReference {
    companion object {
        const val TYPE = "Double"
    }

    override fun getVariable(name: String): Variable = Variable().also {
        it.name = name
        it.value = double?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }
    override fun getSetVariableResponse(): SetVariableResponse = SetVariableResponse().also {
        it.value = double?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }
    override fun setValue(value: String) {
        double = doubleSetter(
            if(VariableValueReference.isNone(value)) null
            else value.toDoubleOrNull()
        )
    }
}