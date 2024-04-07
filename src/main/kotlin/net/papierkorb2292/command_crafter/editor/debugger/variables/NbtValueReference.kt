package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import net.minecraft.nbt.*
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable

class NbtValueReference(
    private val mapper: VariablesReferenceMapper,
    private var nbt: NbtElement,
    private val nbtSetter: (NbtElement?) -> NbtElement?
): VariableValueReference {

    companion object {
        fun getTypeName(nbtType: NbtType<*>) = "NBT: ${nbtType.crashReportName}"
    }

    private var variablesReferencer: CountedVariablesReferencer? = null
    private var variablesReferencerId: Int? = null

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        it.value = nbt.asString()
        it.type = getTypeName(nbt.nbtType)
        it.variablesReference = getVariablesReferencerId()
        variablesReferencer?.run {
            it.namedVariables = this.namedVariableCount
            it.indexedVariables = this.indexedVariableCount
        }
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        it.value = nbt.asString()
        it.type = getTypeName(nbt.nbtType)
        it.variablesReference = getVariablesReferencerId()
        variablesReferencer?.run {
            it.namedVariables = this.namedVariableCount
            it.indexedVariables = this.indexedVariableCount
        }
    }

    private fun getVariablesReferencerId() = variablesReferencerId ?:
        nbt.run {
            when (this) {
                is NbtCompound -> {
                    mapper.addVariablesReferencer(NbtCompoundVariablesReferencer(mapper, this) {
                        val value = nbtSetter(it) ?: NbtEnd.INSTANCE
                        nbt = value
                        if(value is NbtCompound) {
                            return@NbtCompoundVariablesReferencer value
                        }
                        variablesReferencerId = null
                        variablesReferencer = null
                        return@NbtCompoundVariablesReferencer NbtCompound()
                    })
                }
                is AbstractNbtList<*> -> createNbtListVariablesReferencer(this)
                else -> 0
            }
        }

    private fun <Content : NbtElement> createNbtListVariablesReferencer(list: AbstractNbtList<Content>): Int {
        val originalContentType = list.heldType
        return mapper.addVariablesReferencer(NbtListVariablesReferencer(mapper, list) {
            val value = nbtSetter(it) ?: NbtEnd.INSTANCE
            nbt = value
            if(value is AbstractNbtList<*> && value.heldType == originalContentType) {
                @Suppress("UNCHECKED_CAST")
                return@NbtListVariablesReferencer value as AbstractNbtList<Content>
            }
            variablesReferencerId = null
            variablesReferencer = null
            return@NbtListVariablesReferencer list
        })
    }

    override fun setValue(value: String) {
        try {
            val element =
                if(VariableValueReference.isNone(value)) null
                else StringNbtReader(StringReader(value)).parseElement()
            nbt = nbtSetter(element) ?: NbtEnd.INSTANCE
        } catch(_: Exception) { }
    }
}