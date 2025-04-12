package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import net.minecraft.nbt.*
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable

class NbtValueReference(
    private val mapper: VariablesReferenceMapper,
    private var nbt: NbtElement,
    private val nbtSetter: (NbtElement?) -> NbtElement?
): VariableValueReference, IdentifiedVariablesReferencer {

    companion object {
        fun getTypeName(nbtType: NbtType<*>) = "NBT: ${nbtType.crashReportName}"
    }

    private var variablesReferencer: CountedVariablesReferencer? = null
    private var variablesReferencerId: Int? = null

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        it.value = nbt.toString()
        it.type = getTypeName(nbt.nbtType)
        it.variablesReference = getVariablesReferencerId()
        variablesReferencer?.run {
            it.namedVariables = this.namedVariableCount
            it.indexedVariables = this.indexedVariableCount
        }
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        it.value = nbt.toString()
        it.type = getTypeName(nbt.nbtType)
        it.variablesReference = getVariablesReferencerId()
        variablesReferencer?.run {
            it.namedVariables = this.namedVariableCount
            it.indexedVariables = this.indexedVariableCount
        }
    }

    override fun getVariablesReferencerId() = variablesReferencerId ?:
        nbt.let {
            when (it) {
                is NbtCompound -> {
                    val variablesReferencer = NbtCompoundVariablesReferencer(mapper, it) { newValue ->
                        val value = nbtSetter(newValue) ?: NbtEnd.INSTANCE
                        nbt = value
                        if(value is NbtCompound) {
                            return@NbtCompoundVariablesReferencer value
                        }
                        variablesReferencerId = null
                        variablesReferencer = null
                        return@NbtCompoundVariablesReferencer NbtCompound()
                    }
                    this.variablesReferencer = variablesReferencer
                    val id = mapper.addVariablesReferencer(variablesReferencer)
                    variablesReferencerId = id
                    id
                }
                is AbstractNbtList -> createNbtListVariablesReferencer(it)
                else -> 0
            }
        }

    private fun createNbtListVariablesReferencer(list: AbstractNbtList): Int {
        val variablesReferencer = NbtListVariablesReferencer(mapper, list) {
            val value = nbtSetter(it) ?: NbtEnd.INSTANCE
            nbt = value
            if(value is AbstractNbtList) {
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
                else StringNbtReader.fromOps(NbtOps.INSTANCE).read(value)
            nbt = nbtSetter(element) ?: NbtEnd.INSTANCE
        } catch(_: Exception) { }
    }
}