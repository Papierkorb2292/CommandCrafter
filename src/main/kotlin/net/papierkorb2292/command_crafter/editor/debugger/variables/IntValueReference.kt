package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.EvaluateResponse

class IntValueReference(
    private var int: Int?,
    private val intSetter: (Int?) -> Int?
): VariableValueReference {

    companion object {
        const val TYPE = "Int"
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = int?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }
    override fun setValue(value: String) {
        int = intSetter(
            if(VariableValueReference.isNone(value)) null
            else value.toIntOrNull()
        )
    }
}