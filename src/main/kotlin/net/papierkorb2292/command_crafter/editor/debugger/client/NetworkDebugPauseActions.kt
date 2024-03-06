package net.papierkorb2292.command_crafter.editor.debugger.client

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.network.PacketByteBuf
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.networking.ByteBufWritable
import net.papierkorb2292.command_crafter.networking.readNullableVarInt
import net.papierkorb2292.command_crafter.networking.write
import net.papierkorb2292.command_crafter.networking.writeNullableVarInt
import org.eclipse.lsp4j.debug.StepInTarget
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
        packetSender.sendPacket(NetworkServerConnection.stepInTargetsRequestPacketChannel, StepInTargetsRequestC2SPacket(frameId, pauseId, requestId).write())
        return future
    }

    override fun continue_() {
        sendAction(DebugPauseAction.CONTINUE, null)
    }

    private fun sendAction(action: DebugPauseAction, granularity: SteppingGranularity?, additionalInfo: Int? = null) {
        packetSender.sendPacket(NetworkServerConnection.debugPauseActionPacketChannel, DebugPauseActionC2SPacket(action, granularity, additionalInfo, pauseId).write())
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

    class DebugPauseActionC2SPacket(val action: DebugPauseAction, val granularity: SteppingGranularity?, val additionalInfo: Int? = null, val pauseId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(
            DebugPauseAction.values()[buf.readVarInt()],
            if(buf.readBoolean()) SteppingGranularity.values()[buf.readVarInt()] else null,
            buf.readNullableVarInt(),
            buf.readUuid()
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeVarInt(action.ordinal)
            val granularity = granularity
            if(granularity == null) {
                buf.writeBoolean(false)
            } else {
                buf.writeBoolean(true)
                buf.writeVarInt(granularity.ordinal)
            }
            buf.writeNullableVarInt(additionalInfo)
            buf.writeUuid(pauseId)
        }
    }

    class StepInTargetsRequestC2SPacket(val frameId: Int, val pauseId: UUID, val requestId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readVarInt(), buf.readUuid(), buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeVarInt(frameId)
            buf.writeUuid(pauseId)
            buf.writeUuid(requestId)
        }
    }
    class StepInTargetsResponseS2CPacket(val requestId: UUID, val response: StepInTargetsResponse): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(
            buf.readUuid(),
            StepInTargetsResponse().also {
                it.targets = Array(buf.readVarInt()) {
                    StepInTarget().also { target ->
                        target.id = buf.readVarInt()
                        target.label = buf.readString()
                        target.line = buf.readNullableVarInt()
                        target.column = buf.readNullableVarInt()
                        target.endLine = buf.readNullableVarInt()
                        target.endColumn = buf.readNullableVarInt()
                    }
                }
            })

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(requestId)
            buf.writeVarInt(response.targets.size)
            for(target in response.targets) {
                buf.writeVarInt(target.id)
                buf.writeString(target.label)
                buf.writeNullableVarInt(target.line)
                buf.writeNullableVarInt(target.column)
                buf.writeNullableVarInt(target.endLine)
                buf.writeNullableVarInt(target.endColumn)
            }
        }
    }
}