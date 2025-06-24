package net.papierkorb2292.command_crafter.client.editor.debugger

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.papierkorb2292.command_crafter.client.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.networking.packets.DebugPauseActionC2SPacket
import net.papierkorb2292.command_crafter.networking.packets.StepInTargetsRequestC2SPacket
import org.eclipse.lsp4j.debug.StepInTargetsResponse
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkDebugPauseActions(private val packetSender: PacketSender, private val pauseId: UUID) : DebugPauseActions {
    override fun next(granularity: SteppingGranularity) {
        sendAction(DebugPauseActionC2SPacket.DebugPauseActionType.NEXT, granularity)
    }

    override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
        sendAction(DebugPauseActionC2SPacket.DebugPauseActionType.STEP_IN, granularity, targetId)
    }

    override fun stepOut(granularity: SteppingGranularity) {
        sendAction(DebugPauseActionC2SPacket.DebugPauseActionType.STEP_OUT, granularity)
    }

    override fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<StepInTargetsResponse>()
        NetworkServerConnection.currentStepInTargetsRequests[requestId] = future
        packetSender.sendPacket(StepInTargetsRequestC2SPacket(frameId, pauseId, requestId))
        return future
    }

    override fun continue_() {
        sendAction(DebugPauseActionC2SPacket.DebugPauseActionType.CONTINUE, null)
    }

    private fun sendAction(action: DebugPauseActionC2SPacket.DebugPauseActionType, granularity: SteppingGranularity?, additionalInfo: Int? = null) {
        packetSender.sendPacket(DebugPauseActionC2SPacket(action, granularity, additionalInfo, pauseId))
    }

}