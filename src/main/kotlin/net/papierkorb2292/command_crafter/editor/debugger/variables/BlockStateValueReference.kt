package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.HolderLookup
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class BlockStateValueReference(
    private val mapper: VariablesReferenceMapper,
    private var state: BlockState?,
    private val holderLookup: HolderLookup<Block>,
    private val stateSetter: (BlockState?) -> (BlockState?)
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        const val TYPE = "BlockState"
    }

    private val valueReferences = mutableMapOf<String, VariableValueReference>()

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        val state = state ?: return
        for(property in state.properties) {
            addPropertyValueReference(state, property)
        }
    }

    private fun <T : Comparable<T>> addPropertyValueReference(state: BlockState, property: Property<T>) {
        val serialized = property.getName(state.getValue(property))
        valueReferences[property.name] = StringValueReference(serialized) { newString ->
            property.getValue(newString ?: return@StringValueReference serialized)
                .map { parsed ->
                    val newState = stateSetter(state.setValue(property, parsed))
                        ?: return@map null
                    property.getName(newState.getValue(property))
                }.orElse(serialized)
        }
    }

    private var variablesReferencerId: Int? = null

    private fun getValue(): String {
        return BlockStateParser.serialize(state ?: return VariableValueReference.NONE_VALUE)
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = getValue()
        it.type = BlockValueReference.Companion.TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun setValue(value: String) {
        if(VariableValueReference.isNone(value)) {
            this.state = stateSetter(null)
            updateValueReferences()
            return
        }
        try {
            val newBlock = BlockStateParser.parseForBlock(holderLookup, StringReader(value), false)
            this.state = stateSetter(newBlock.blockState)
            updateValueReferences()
        } catch(_: CommandSyntaxException) { }
    }

    override val namedVariableCount: Int
        get() = valueReferences.size
    override val indexedVariableCount: Int
        get() = 0

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, null, valueReferences)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, null, valueReferences)
}