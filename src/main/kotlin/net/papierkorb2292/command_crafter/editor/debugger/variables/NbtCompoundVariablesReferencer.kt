package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
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

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, null, valueReferences)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?>  =
        VariablesReferencer.setVariablesFromCollection(args, null, valueReferences)

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