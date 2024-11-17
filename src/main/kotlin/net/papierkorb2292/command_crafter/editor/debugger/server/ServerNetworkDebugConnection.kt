package net.papierkorb2292.command_crafter.editor.debugger.server

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.helper.ReservedBreakpointIdStart
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.networking.packets.*
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.set

class ServerNetworkDebugConnection(
    player: ServerPlayerEntity,
    val clientEditorDebugConnection: UUID,
    override val oneTimeDebugTarget: EditorDebugConnection.DebugTarget? = null,
    override var nextSourceReference: Int = 1,
    override val suspendServer: Boolean = true
) : EditorDebugConnection {
    override val lifecycle = EditorDebugConnection.Lifecycle()

    var currentPauseId: UUID? = null
        private set

    val networkHandler: ServerPlayNetworkHandler = player.networkHandler

    private val packetSender = ServerPlayNetworking.getSender(player)
    private val playerName = player.name.string

    init {
        lifecycle.shouldExitEvent.thenAccept {
            packetSender.sendPacket(DebuggerExitS2CPacket(it, clientEditorDebugConnection))
        }
    }

    override fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer) {
        val pauseId = NetworkServerConnectionHandler.addServerDebugPause(DebugPauseInformation(actions, variables, clientEditorDebugConnection))
        currentPauseId = pauseId
        packetSender.sendPacket(
            PausedUpdateS2CPacket(clientEditorDebugConnection, pauseId to args)
        )
    }

    override fun pauseEnded() {
        currentPauseId?.run {
            NetworkServerConnectionHandler.removeServerDebugPauseHandler(this)
        }
        currentPauseId = null
        packetSender.sendPacket(
            PausedUpdateS2CPacket(clientEditorDebugConnection, null)
        )
    }

    override fun isPaused() = currentPauseId != null

    override fun updateReloadedBreakpoint(update: BreakpointEventArguments) {
        packetSender.sendPacket(UpdateReloadedBreakpointS2CPacket(update, clientEditorDebugConnection))
    }

    override fun reserveBreakpointIds(count: Int): CompletableFuture<ReservedBreakpointIdStart> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<ReservedBreakpointIdStart>()
        packetSender.sendPacket(ReserveBreakpointIdsRequestS2CPacket(count, clientEditorDebugConnection, requestId))
        NetworkServerConnectionHandler.currentBreakpointIdsRequests[requestId] = future
        return future
    }

    override fun popStackFrames(stackFrames: Int) {
        packetSender.sendPacket(PopStackFramesS2CPacket(stackFrames, clientEditorDebugConnection))
    }

    override fun pushStackFrames(stackFrames: List<MinecraftStackFrame>) {
        packetSender.sendPacket(PushStackFramesS2CPacket(stackFrames, clientEditorDebugConnection))
    }

    override fun output(args: OutputEventArguments) {
        packetSender.sendPacket(DebuggerOutputS2CPacket(args, clientEditorDebugConnection))
    }

    override fun onSourceReferenceAdded() {
        nextSourceReference++
        packetSender.sendPacket(SourceReferenceAddedS2CPacket(clientEditorDebugConnection))
    }

    override fun toString(): String {
        return "ServerNetworkDebugConnection(player=${playerName})"
    }

    class DebugPauseInformation(val actions: DebugPauseActions, val pauseContext: VariablesReferencer, val clientEditorDebugConnection: UUID)

}
