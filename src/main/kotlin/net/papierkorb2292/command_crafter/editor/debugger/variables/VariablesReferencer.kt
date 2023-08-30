package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.network.PacketByteBuf
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

interface VariablesReferencer {
    fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>>
    fun setVariable(args: SetVariableArguments): CompletableFuture<SetVariableResult?>

    data class SetVariableResult(
        val response: SetVariableResponse,
        val invalidateVariables: Boolean = false,
    ): ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(
            SetVariableResponse().apply {
                value = buf.readString()
                type = buf.readNullableString()
                variablesReference = buf.readNullableInt()
                namedVariables = buf.readNullableInt()
                indexedVariables = buf.readNullableInt()
            },
            buf.readBoolean()
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeString(response.value)
            buf.writeNullableString(response.type)
            buf.writeNullableInt(response.variablesReference)
            buf.writeNullableInt(response.namedVariables)
            buf.writeNullableInt(response.indexedVariables)
            buf.writeBoolean(invalidateVariables)
        }
    }
}