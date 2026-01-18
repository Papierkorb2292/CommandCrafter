package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable

interface VariableValueReference {
    companion object {
        const val NONE_VALUE = "None"

        fun isNone(value: String): Boolean {
            val lowercase = value.lowercase()
            return lowercase == "n" || lowercase == "none" || lowercase == "null"
        }
    }

    fun getVariable(name: String): Variable {
        val evaluation = getEvaluateResponse()
        return Variable().also {
            it.name = name
            it.value = evaluation.result
            it.type = evaluation.type
            it.variablesReference = evaluation.variablesReference
            it.namedVariables = evaluation.namedVariables
            it.indexedVariables = evaluation.indexedVariables
            it.valueLocationReference = evaluation.valueLocationReference
        }
    }
    fun getSetVariableResponse(): SetVariableResponse {
        val evaluation = getEvaluateResponse()
        return SetVariableResponse().also {
            it.value = evaluation.result
            it.type = evaluation.type
            it.variablesReference = evaluation.variablesReference
            it.namedVariables = evaluation.namedVariables
            it.indexedVariables = evaluation.indexedVariables
            it.valueLocationReference = evaluation.valueLocationReference
        }
    }
    fun getEvaluateResponse(): EvaluateResponse
    fun setValue(value: String)
}