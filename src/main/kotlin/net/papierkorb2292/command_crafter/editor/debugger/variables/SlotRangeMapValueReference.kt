package net.papierkorb2292.command_crafter.editor.debugger.variables

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.inventory.SlotRange
import net.minecraft.world.inventory.SlotRanges
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class SlotRangeMapValueReference(
    val mapper: VariablesReferenceMapper,
    val provider: SlotProvider,
    val slotRange: SlotRange,
    val includeName: Boolean,
    val registries: HolderLookup.Provider,
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        const val TYPE = "Slot-Range-Map"

        private val slotNameCache: MutableMap<SlotRange, Int2ObjectMap<String>> = mutableMapOf()

        private fun getNameForSlot(slotRange: SlotRange, slot: Int): String {
            val slotNames = slotNameCache.getOrPut(slotRange) {
                val rangeName = slotRange.serializedName
                val prefix = rangeName.substring(0, rangeName.lastIndexOf('.'))
                val map = Int2ObjectOpenHashMap<String>()
                for(slotName in SlotRanges.singleSlotNames()) {
                    if(!slotName.startsWith(prefix))
                        continue
                    map.put(SlotRanges.nameToIds(slotName)!!.slots().getInt(0), slotName)
                }
                map
            }
            val name = slotNames[slot] ?: return slot.toString()
            return "$name ($slot)"
        }
    }

    private val valueReferences: Map<String, VariableValueReference> = slotRange.slots().associate { slot ->
        getNameForSlot(slotRange, slot) to SlotAccessValueReference(mapper, provider, slot, false, registries)
    }

    override val namedVariableCount: Int
        get() = valueReferences.size
    override val indexedVariableCount: Int
        get() = 0

    private var variablesReferencerId: Int? = null

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, null, valueReferences)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, null, valueReferences)


    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = if(valueReferences.size == 1) "Slot Range Map [1 slot]" else "Slot Range Map [${valueReferences.size} slots]"
        if(includeName) {
            it.result = "${SlotProviderMapValueReference.getNameForProvider(provider)}: ${it.result}"
        }
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) { }
}