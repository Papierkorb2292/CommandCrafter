package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter
import java.util.concurrent.CompletableFuture

class NbtCompoundVariablesReferencer(
    private val mapper: VariablesReferenceMapper,
    private var nbtCompound: CompoundTag,
    private val nbtSetter: (CompoundTag) -> CompoundTag
) : CountedVariablesReferencer {

    private val valueReferences = HashMap<String, VariableValueReference>()
    init {
        valueReferences.putAll(nbtCompound.keySet().map { key ->
            key to createValueReference(key, nbtCompound[key]!!)
        })
    }

    override val namedVariableCount: Int
        get() = nbtCompound.size()
    override val indexedVariableCount: Int
        get() = 0

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        if(args.filter == VariablesArgumentsFilter.INDEXED) {
            return CompletableFuture.completedFuture(arrayOf())
        }
        val keys = nbtCompound.keySet()
        val keysIterator = keys.iterator()
        val start = args.start
        if(start != null) {
            for (i in 0 until start) {
                if (!keysIterator.hasNext()) {
                    return CompletableFuture.completedFuture(arrayOf())
                }
                keysIterator.next()
            }
        }
        val variables = mutableListOf<Variable>()
        val count = args.count
        var i = 0
        while(keysIterator.hasNext() && (count == null || i++ < count)) {
            val key = keysIterator.next()
            variables += (valueReferences[key] ?: continue).getVariable(key)
        }
        return CompletableFuture.completedFuture(variables.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val valueReference = valueReferences[args.name]
        if(valueReference != null) {
            valueReference.setValue(args.value)
            return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(valueReference.getSetVariableResponse(), true))
        }
        return CompletableFuture.completedFuture(null)
    }

    private fun createValueReference(key: String, element: Tag): VariableValueReference {
        return NbtValueReference(mapper, element) {
            val newCompound = nbtCompound.copy()
            if(it != null) {
                newCompound.put(key, it)
            } else {
                newCompound.remove(key)
            }
            nbtCompound = nbtSetter(newCompound)
            updateValueReferences()
            newCompound[key]
        }
    }

    private fun updateValueReferences() {
        val existingKeysIterator = valueReferences.keys.iterator()
        while(existingKeysIterator.hasNext()) {
            val key = existingKeysIterator.next()
            if(key !in nbtCompound) {
                existingKeysIterator.remove()
            }
        }

        val compoundKeysIterator = nbtCompound.keySet().iterator()
        while(compoundKeysIterator.hasNext()) {
            val key = compoundKeysIterator.next()
            if(key !in valueReferences) {
                valueReferences[key] = createValueReference(key, nbtCompound[key]!!)
            }
        }
    }
}