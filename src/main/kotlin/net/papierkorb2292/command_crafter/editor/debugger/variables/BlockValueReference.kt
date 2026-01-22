package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class BlockValueReference (
    private val mapper: VariablesReferenceMapper,
    private var state: BlockState?,
    private var nbt: CompoundTag?,
    private val holderLookup: HolderLookup<Block>,
    private val blockSetter: (BlockState?, CompoundTag?) -> Pair<BlockState?, CompoundTag?>
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {

    companion object {
        const val TYPE = "Block"
        const val STATE_VARIABLE_NAME = "state"
        const val NBT_VARIABLE_NAME = "NBT"
    }

    private val valueReferences = mutableMapOf<String, VariableValueReference>()

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        val state = state ?: return
        valueReferences[STATE_VARIABLE_NAME] = BlockStateValueReference(mapper, state, holderLookup) { newState ->
            val (state, nbt) = blockSetter(newState, nbt)
            this.state = state
            this.nbt = nbt
            updateValueReferences()
            state
        }
        val nbt = nbt ?: return
        valueReferences[NBT_VARIABLE_NAME] = NbtValueReference(mapper, nbt) { newNbt ->
            if(newNbt !is CompoundTag?)
                return@NbtValueReference nbt
            val (state, nbt) = blockSetter(state, newNbt)
            this.state = state
            this.nbt = nbt
            updateValueReferences()
            nbt
        }
    }

    private var variablesReferencerId: Int? = null

    private fun getValue(): String {
        return BlockStateParser.serialize(state ?: return VariableValueReference.NONE_VALUE) + (nbt?.toString() ?: "")
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = getValue()
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun setValue(value: String) {
        if(VariableValueReference.isNone(value)) {
            val (state, nbt) = blockSetter(null, null)
            this.state = state
            this.nbt = nbt
            updateValueReferences()
            return
        }
        try {
            val newBlock = BlockStateParser.parseForBlock(holderLookup, StringReader(value), true)
            val (state, nbt) = blockSetter(newBlock.blockState, newBlock.nbt)
            this.state = state
            this.nbt = nbt
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