package net.papierkorb2292.command_crafter.editor.debugger.variables

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.papierkorb2292.command_crafter.networking.SET_VARIABLE_RESPONSE_PACKET_CODEC
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
    ) {
        companion object {
            val PACKET_CODEC: PacketCodec<ByteBuf, SetVariableResult> = PacketCodec.tuple(
                SET_VARIABLE_RESPONSE_PACKET_CODEC,
                SetVariableResult::response,
                PacketCodecs.BOOL,
                SetVariableResult::invalidateVariables,
                ::SetVariableResult
            )
        }
    }
}