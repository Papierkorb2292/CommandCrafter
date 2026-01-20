package net.papierkorb2292.command_crafter.editor.debugger.variables

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.papierkorb2292.command_crafter.networking.SET_VARIABLE_RESPONSE_PACKET_CODEC
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

interface VariablesReferencer {
    companion object {
        fun getVariablesFromCollection(
            args: VariablesArguments,
            indexedValueReferences: Collection<VariableValueReference>?,
            namedValueReferences: Map<String, VariableValueReference>?
        ): CompletableFuture<Array<Variable>> {
            if(args.filter == VariablesArgumentsFilter.INDEXED) {
                if(indexedValueReferences == null)
                    return CompletableFuture.completedFuture(arrayOf())
                val start = args.start ?: 0
                val count = args.count ?: (indexedValueReferences.size - start)
                return CompletableFuture.completedFuture(indexedValueReferences.drop(start).take(count).mapIndexed { index, value ->
                    value.getVariable((start + index).toString())
                }.toTypedArray())
            }
            if(namedValueReferences == null)
                return CompletableFuture.completedFuture(arrayOf())
            val start = args.start ?: 0
            val count = args.count ?: (namedValueReferences.size - start)
            return CompletableFuture.completedFuture(namedValueReferences.entries.drop(start).take(count).map {
                    (name, value) -> value.getVariable(name)
            }.toTypedArray())
        }

        fun setVariablesFromCollection(
            args: SetVariableArguments,
            indexedValueReferences: Collection<VariableValueReference>?,
            namedValueReferences: Map<String, VariableValueReference>?
        ): CompletableFuture<SetVariableResult?> {
            val index = args.name.toIntOrNull()
            if(index != null && indexedValueReferences != null) {
                if(index >= 0 && index < indexedValueReferences.size) {
                    val valueReference = indexedValueReferences.elementAt(index)
                    valueReference.setValue(args.value)
                    return CompletableFuture.completedFuture(SetVariableResult(valueReference.getSetVariableResponse(), true))
                }
                return CompletableFuture.completedFuture(null)
            }
            val valueReference = namedValueReferences?.get(args.name)
                ?: return CompletableFuture.completedFuture(null)
            valueReference.setValue(args.value)
            return CompletableFuture.completedFuture(SetVariableResult(valueReference.getSetVariableResponse(), true))
        }
    }

    fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>>
    fun setVariable(args: SetVariableArguments): CompletableFuture<SetVariableResult?>

    data class SetVariableResult(
        val response: SetVariableResponse,
        val invalidateVariables: Boolean = false,
    ) {
        companion object {
            val PACKET_CODEC: StreamCodec<ByteBuf, SetVariableResult> = StreamCodec.composite(
                SET_VARIABLE_RESPONSE_PACKET_CODEC,
                SetVariableResult::response,
                ByteBufCodecs.BOOL,
                SetVariableResult::invalidateVariables,
                ::SetVariableResult
            )
        }
    }
}