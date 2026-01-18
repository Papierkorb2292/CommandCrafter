package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.EvaluateResponse

class BooleanValueReference(
    private var boolean: Boolean?,
    private val booleanSetter: (Boolean?) -> Boolean?
): VariableValueReference {
    companion object {
        const val TYPE = "Bool"
    }

    override fun getEvaluateResponse(): EvaluateResponse = EvaluateResponse().also {
        it.result = boolean?.toString() ?: VariableValueReference.NONE_VALUE
        it.type = TYPE
    }
    override fun setValue(value: String) {
        boolean = booleanSetter(
            if(VariableValueReference.isNone(value)) null
            else value.lowercase().toBooleanStrictOrNull() ?: value.toIntOrNull()?.let { it != 0 }
        )
    }
}