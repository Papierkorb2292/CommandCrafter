package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.util.Identifier
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable

class IdValueReference(
    private var id: Identifier?,
    private val idSetter: (Identifier?) -> Identifier?
): VariableValueReference {
    override fun getVariable(name: String) = Variable().also {
        it.name = name
        it.value = id?.toString() ?: "None"
        it.type = "Identifier"
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        it.value = id?.toString() ?: "None"
        it.type = "Identifier"
    }

    override fun setValue(value: String) {
        id = idSetter(
            if(VariableValueReference.isNone(value)) null
            else Identifier.tryParse(value)
        )
    }

}