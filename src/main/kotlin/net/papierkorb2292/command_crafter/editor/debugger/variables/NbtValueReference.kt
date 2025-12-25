package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import net.minecraft.nbt.*
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable

class NbtValueReference(
    private val mapper: VariablesReferenceMapper,
    private var nbt: Tag,
    private val nbtSetter: (Tag?) -> Tag?
): VariableValueReference, IdentifiedVariablesReferencer {

    companion object {
        fun getTypeName(nbtType: TagType<*>) = "NBT: ${nbtType.name}"
    }

    private var variablesReferencer: CountedVariablesReferencer? = null
    private var variablesReferencerId: Int? = null

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        it.value = nbt.toString()
        it.type = getTypeName(nbt.type)
        it.variablesReference = getVariablesReferencerId()
        variablesReferencer?.run {
            it.namedVariables = this.namedVariableCount
            it.indexedVariables = this.indexedVariableCount
        }
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        it.value = nbt.toString()
        it.type = getTypeName(nbt.type)
        it.variablesReference = getVariablesReferencerId()
        variablesReferencer?.run {
            it.namedVariables = this.namedVariableCount
            it.indexedVariables = this.indexedVariableCount
        }
    }

    override fun getVariablesReferencerId() = variablesReferencerId ?:
        nbt.let {
            when (it) {
                is CompoundTag -> {
                    val variablesReferencer = NbtCompoundVariablesReferencer(mapper, it) { newValue ->
                        val value = nbtSetter(newValue) ?: EndTag.INSTANCE
                        nbt = value
                        if(value is CompoundTag) {
                            return@NbtCompoundVariablesReferencer value
                        }
                        variablesReferencerId = null
                        variablesReferencer = null
                        return@NbtCompoundVariablesReferencer CompoundTag()
                    }
                    this.variablesReferencer = variablesReferencer
                    val id = mapper.addVariablesReferencer(variablesReferencer)
                    variablesReferencerId = id
                    id
                }
                is CollectionTag -> createNbtListVariablesReferencer(it)
                else -> 0
            }
        }

    private fun createNbtListVariablesReferencer(list: CollectionTag): Int {
        val variablesReferencer = NbtListVariablesReferencer(mapper, list) {
            val value = nbtSetter(it) ?: EndTag.INSTANCE
            nbt = value
            if(value is CollectionTag) {
                return@NbtListVariablesReferencer value
            }
            variablesReferencerId = null
            variablesReferencer = null
            return@NbtListVariablesReferencer list
        }
        this.variablesReferencer = variablesReferencer
        val id = mapper.addVariablesReferencer(variablesReferencer)
        variablesReferencerId = id
        return id
    }

    override fun setValue(value: String) {
        try {
            val element =
                if(VariableValueReference.isNone(value)) null
                else TagParser.create(NbtOps.INSTANCE).parseFully(value)
            nbt = nbtSetter(element) ?: EndTag.INSTANCE
        } catch(_: Exception) { }
    }
}