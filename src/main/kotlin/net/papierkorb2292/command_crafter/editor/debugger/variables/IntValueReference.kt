package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable

class IntValueReference(
    private var int: Int?,
    private val intSetter: (Int?) -> Int?
): VariableValueReference {

    companion object {
        const val TYPE = "Int"
    }

    override fun getVariable(name: String): Variable = Variable().also {
        it.name = name
        it.value = int?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }
    override fun getSetVariableResponse(): SetVariableResponse = SetVariableResponse().also {
        it.value = int?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }
    override fun setValue(value: String) {
        int = intSetter(
            if(VariableValueReference.isNone(value)) null
            else value.toIntOrNull()
        )
    }
}