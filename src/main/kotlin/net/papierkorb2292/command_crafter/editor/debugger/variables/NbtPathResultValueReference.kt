package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.nbt.Tag
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class NbtPathResultValueReference(
    private val mapper: VariablesReferenceMapper,
    private var results: List<Tag>,
    private val setter: ((List<Tag>) -> List<Tag>)
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {

    companion object {
        const val TYPE = "NBT-Path-Result"
    }

    private lateinit var valueReferences: List<VariableValueReference>

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences = results.mapIndexed { i, entry ->
            NbtValueReference(mapper, entry) { newValue ->
                if(newValue == null) return@NbtValueReference entry
                val newResults = results.toMutableList()
                newResults[results.indexOf(entry)] = newValue
                results = setter(newResults)
                updateValueReferences()
                newResults[i]
            }
        }
    }

    override fun getEvaluateResponse() = EvaluateResponse().apply {
        result = if(results.size == 1) "NBT Path Result [1 entry]" else "NBT Path Result [${results.size} entries]"
        type = TYPE
        variablesReference = getVariablesReferencerId()
        namedVariables = namedVariableCount
        indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) { }

    override val namedVariableCount: Int
        get() = 0
    override val indexedVariableCount: Int
        get() = valueReferences.size

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, valueReferences, null)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, valueReferences, null)

    private var variablesReferencerId: Int? = null

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }
}