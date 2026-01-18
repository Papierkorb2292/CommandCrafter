package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.resources.Identifier
import org.eclipse.lsp4j.debug.EvaluateResponse

class IdValueReference(
    private var id: Identifier?,
    private val idSetter: (Identifier?) -> Identifier?
): VariableValueReference {

    companion object {
        const val TYPE = "Identifier"
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = id?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }

    override fun setValue(value: String) {
        id = idSetter(
            if(VariableValueReference.isNone(value)) null
            else Identifier.tryParse(value)
        )
    }

}