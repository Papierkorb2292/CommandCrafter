package net.papierkorb2292.command_crafter.client.editor.debugger

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.papierkorb2292.command_crafter.client.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.networking.packets.GetVariablesRequestC2SPacket
import net.papierkorb2292.command_crafter.networking.packets.SetVariableRequestC2SPacket
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkVariablesReferencer(val packetSender: PacketSender, val pauseId: UUID) :
    VariablesReferencer {
    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<Array<Variable>>()
        NetworkServerConnection.currentGetVariablesRequests[requestId] = future
        packetSender.sendPacket(
            GetVariablesRequestC2SPacket(pauseId, requestId, args)
        )
        return future
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<VariablesReferencer.SetVariableResult?>()
        NetworkServerConnection.currentSetVariableRequests[requestId] = future
        packetSender.sendPacket(
            SetVariableRequestC2SPacket(pauseId, requestId, args)
        )
        return future
    }

}
