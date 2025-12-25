package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.Tag
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter
import java.util.concurrent.CompletableFuture

class NbtListVariablesReferencer(
    private val mapper: VariablesReferenceMapper,
    private var nbtList: CollectionTag,
    private val nbtSetter: (CollectionTag) -> CollectionTag
) : CountedVariablesReferencer {
    override val namedVariableCount: Int
        get() = 0
    override val indexedVariableCount: Int
        get() = nbtList.size()

    private val valueReferences = mutableListOf<VariableValueReference>()
    init {
        valueReferences.addAll(nbtList.mapIndexed { index, element ->
            createValueReference(index, element)
        })
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        if(args.filter == VariablesArgumentsFilter.NAMED) {
            return CompletableFuture.completedFuture(arrayOf())
        }
        val count = args.count
        val variables = mutableListOf<Variable>()
        val valueReferencesSize = valueReferences.size
        var i = args.start ?: 0
        val end = if(count == null) valueReferencesSize else i + count
        while(i < end) {
            variables += valueReferences[i].getVariable(i.toString())
            i++
        }
        return CompletableFuture.completedFuture(variables.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        try {
            val index = args.name.toInt()
            if(index >= 0 && index < valueReferences.size) {
                val valueReference = valueReferences[index]
                valueReference.setValue(args.value)
                return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(valueReference.getSetVariableResponse(), true))
            }
        } catch(_: NumberFormatException) {  }
        return CompletableFuture.completedFuture(null)
    }

    private fun createValueReference(index: Int, element: Tag): VariableValueReference {
        return NbtValueReference(mapper, element) {
            val newList = nbtList.copy() as CollectionTag
            if(it != null) {
                newList.setTag(index, it)
            } else {
                newList.remove(index)
            }
            nbtList = nbtSetter(newList)
            updateValueReferences()
            // get
            nbtList.get(index)
        }
    }

    private fun updateValueReferences() {
        if(nbtList.size() < valueReferences.size) {
            valueReferences.subList(nbtList.size(), valueReferences.size).clear()
            return
        }
        if(nbtList.size() > valueReferences.size) {
            valueReferences.addAll(
                nbtList.size() - valueReferences.size,
                (valueReferences.size until nbtList.size()).map { index ->
                    createValueReference(index, nbtList.get(index))
                }
            )
        }
    }
}