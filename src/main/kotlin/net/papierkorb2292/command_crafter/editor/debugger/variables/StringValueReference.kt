package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.apache.commons.lang3.StringEscapeUtils
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableResponse

class StringValueReference(
    private var string: String?,
    private val stringSetter: (String?) -> String?
): VariableValueReference {

    companion object {
        const val TYPE = "String"
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = constructValue()
        it.type = TYPE
    }

    private fun constructValue(): String = string?.run {
        @Suppress("DEPRECATION")
        "\"${StringEscapeUtils.escapeJava(this)}\""
    } ?: VariableValueReference.NONE_VALUE

    override fun setValue(value: String) {
        string = stringSetter(
            if(VariableValueReference.isNone(value)) null
            else if(value.startsWith("\"") && value.endsWith("\"")) StringEscapeUtils.unescapeJava(value.substring(1, value.length - 1))
            else return
        )
    }
}