package net.papierkorb2292.command_crafter.editor.debugger.variables

import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class StringMapValueReference(
    private val mapper: VariablesReferenceMapper,
    private var values: Map<String, String?>,
    private val setter: ((Map<String, String?>) -> Unit)? = null
) : VariableValueReference, CountedVariablesReferencer {
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

    fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        if(args.filter == VariablesArgumentsFilter.INDEXED) return CompletableFuture.completedFuture(emptyArray())
        val start = args.start ?: 0
        val count = args.count ?: (content.size - start)
        return CompletableFuture.completedFuture(content.entries.drop(start).take(count).map {
                (name, value) -> value.getVariable(name)
        }.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val valueReference = content[args.name]
            ?: return CompletableFuture.completedFuture(null)
        valueReference.setValue(args.value)
        return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(valueReference.getSetVariableResponse(), true))
    }

    private fun constructValue() = values.map { (name, value) -> "$name: $value" }.joinToString(", ")

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        it.value = constructValue()
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        it.value = constructValue()
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) { }
}