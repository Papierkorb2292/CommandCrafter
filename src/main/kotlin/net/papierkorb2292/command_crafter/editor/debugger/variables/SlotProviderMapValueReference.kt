package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.core.HolderLookup
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.inventory.SlotRange
import net.minecraft.world.level.block.entity.BlockEntity
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class SlotProviderMapValueReference(
    val mapper: VariablesReferenceMapper,
    val providers: List<SlotProvider>,
    val slotRange: SlotRange,
    val registries: HolderLookup.Provider,
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {

    companion object {
        const val TYPE = "Slot-Provider-Map"

        fun getNameForProvider(provider: SlotProvider): String =
            when(provider) {
                is Entity -> provider.scoreboardName
                is BlockEntity -> provider.blockState.block.builtInRegistryHolder().registeredName
                else -> provider.toString()
            }
    }

    private val valueReferences: Map<String, VariableValueReference> =
        if(slotRange.size() == 1)
            providers.associate { getNameForProvider(it) to SlotAccessValueReference(mapper, it, slotRange.slots().getInt(0), false, registries) }
        else
            providers.associate { getNameForProvider(it) to SlotRangeMapValueReference(mapper, it, slotRange, false, registries) }

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
        it.result = if(valueReferences.size == 1) "Slot Provider Map [1 entry]" else "Slot Provider Map [${valueReferences.size} entries]"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) { }
}