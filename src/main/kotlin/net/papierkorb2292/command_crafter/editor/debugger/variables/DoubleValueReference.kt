package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.EvaluateResponse

class DoubleValueReference(
    private var double: Double?,
    private val doubleSetter: (Double?) -> Double?
): VariableValueReference {
    companion object {
        const val TYPE = "Double"
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = double?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }
    override fun setValue(value: String) {
        double = doubleSetter(
            if(VariableValueReference.isNone(value)) null
            else value.toDoubleOrNull()
        )
    }
}