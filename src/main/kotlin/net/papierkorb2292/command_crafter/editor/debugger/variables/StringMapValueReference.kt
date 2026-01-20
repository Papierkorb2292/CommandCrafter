package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class StringMapValueReference(
    private val mapper: VariablesReferenceMapper,
    private var values: Map<String, String?>,
    private val setter: ((Map<String, String?>) -> Unit)? = null
) : VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        const val TYPE = "String-Map"
    }

    private val content = values.mapValues {
        (name, value) -> StringValueReference(value) { newValue ->
            val setter = setter ?: return@StringValueReference this.values[name]
            this.values += (name to newValue)
            setter(this.values)
            newValue
        }
    }

    override val namedVariableCount: Int
        get() = content.size
    override val indexedVariableCount: Int
        get() = 0

    private var variablesReferencerId: Int? = null

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, null, content)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?>  =
        VariablesReferencer.setVariablesFromCollection(args, null, content)

    private fun constructValue() = values.map { (name, value) -> "$name: $value" }.joinToString(", ")

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = constructValue()
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) { }
}