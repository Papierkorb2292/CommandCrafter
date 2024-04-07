package net.papierkorb2292.command_crafter.editor.debugger.variables

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

    fun getVariable(name: String): Variable
    fun getSetVariableResponse(): SetVariableResponse
    fun setValue(value: String)
}