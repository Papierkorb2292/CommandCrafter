package net.papierkorb2292.command_crafter.editor.debugger.client

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.network.PacketByteBuf
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.networking.ByteBufWritable
import net.papierkorb2292.command_crafter.networking.write
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*

class NetworkDebugPauseActions(private val packetSender: PacketSender, private val pauseId: UUID) : DebugPauseActions {
    override fun next(granularity: SteppingGranularity) {
        sendAction(DebugPauseAction.NEXT, granularity)
    }

    override fun stepIn(granularity: SteppingGranularity) {
        sendAction(DebugPauseAction.STEP_IN, granularity)
    }

    override fun stepOut(granularity: SteppingGranularity) {
        sendAction(DebugPauseAction.STEP_OUT, granularity)
    }

    override fun continue_() {
        sendAction(DebugPauseAction.CONTINUE, null)
    }

    private fun sendAction(action: DebugPauseAction, granularity: SteppingGranularity?) {
        packetSender.sendPacket(NetworkServerConnection.debugPauseActionPacketChannel, DebugPauseActionC2SPacket(action, granularity, pauseId).write())
    }

    enum class DebugPauseAction {
        NEXT,
        STEP_IN,
        STEP_OUT,
        CONTINUE;

        fun apply(actions: DebugPauseActions, granularity: SteppingGranularity?) {
            when(this) {
                NEXT -> actions.next(granularity ?: SteppingGranularity.STATEMENT)
                STEP_IN -> actions.stepIn(granularity ?: SteppingGranularity.STATEMENT)
                STEP_OUT -> actions.stepOut(granularity ?: SteppingGranularity.STATEMENT)
                CONTINUE -> actions.continue_()
            }
        }
    }

    class DebugPauseActionC2SPacket(val action: DebugPauseAction, val granularity: SteppingGranularity?, val pauseId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(
            DebugPauseAction.values()[buf.readVarInt()],
            if(buf.readBoolean()) SteppingGranularity.values()[buf.readVarInt()] else null,
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
            buf.writeUuid(pauseId)
        }
    }
}