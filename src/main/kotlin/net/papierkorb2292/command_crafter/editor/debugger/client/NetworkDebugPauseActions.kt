package net.papierkorb2292.command_crafter.editor.debugger.client

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.networking.packets.DebugPauseActionC2SPacket
import net.papierkorb2292.command_crafter.networking.packets.StepInTargetsRequestC2SPacket
import org.eclipse.lsp4j.debug.StepInTargetsResponse
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkDebugPauseActions(private val packetSender: PacketSender, private val pauseId: UUID) : DebugPauseActions {
    override fun next(granularity: SteppingGranularity) {
        sendAction(DebugPauseAction.NEXT, granularity)
    }

    override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
        sendAction(DebugPauseAction.STEP_IN, granularity, targetId)
    }

    override fun stepOut(granularity: SteppingGranularity) {
        sendAction(DebugPauseAction.STEP_OUT, granularity)
    }

    override fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<StepInTargetsResponse>()
        NetworkServerConnection.currentStepInTargetsRequests[requestId] = future
        packetSender.sendPacket(StepInTargetsRequestC2SPacket(frameId, pauseId, requestId))
        return future
    }

    override fun continue_() {
        sendAction(DebugPauseAction.CONTINUE, null)
    }

    private fun sendAction(action: DebugPauseAction, granularity: SteppingGranularity?, additionalInfo: Int? = null) {
        packetSender.sendPacket(DebugPauseActionC2SPacket(action, granularity, additionalInfo, pauseId))
    }

    enum class DebugPauseAction {
        NEXT,
        STEP_IN,
        STEP_OUT,
        CONTINUE;

        fun apply(actions: DebugPauseActions, packet: DebugPauseActionC2SPacket) {
            when(this) {
                NEXT -> actions.next(packet.granularity ?: SteppingGranularity.STATEMENT)
                STEP_IN -> actions.stepIn(packet.granularity ?: SteppingGranularity.STATEMENT, packet.additionalInfo)
                STEP_OUT -> actions.stepOut(packet.granularity ?: SteppingGranularity.STATEMENT)
                CONTINUE -> actions.continue_()
            }
        }
    }

}