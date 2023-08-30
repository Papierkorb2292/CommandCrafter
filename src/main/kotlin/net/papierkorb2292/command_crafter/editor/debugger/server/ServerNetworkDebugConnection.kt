package net.papierkorb2292.command_crafter.editor.debugger.server

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.helper.readBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.helper.writeBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.StoppedEventArguments
import java.util.*

class ServerNetworkDebugConnection(val player: ServerPlayerEntity, val clientEditorDebugConnection: UUID) : EditorDebugConnection {
    private var currentPauseId: UUID? = null
    private val packetSender = ServerPlayNetworking.getSender(player)

    override fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer) {
        val pauseId = NetworkServerConnection.addServerDebugPause(actions to variables)
        currentPauseId = pauseId
        packetSender.sendPacket(
            NetworkServerConnection.setDebuggerPausedPacketChannel,
            PausedUpdateS2CPacket(clientEditorDebugConnection, pauseId to args).write()
        )
    }

    override fun pauseEnded() {
        currentPauseId?.run {
            NetworkServerConnection.removeServerDebugPauseHandler(this)
        }
        currentPauseId = null
        packetSender.sendPacket(
            NetworkServerConnection.setDebuggerPausedPacketChannel,
            PausedUpdateS2CPacket(clientEditorDebugConnection, null).write()
        )
    }

    override fun isPaused() = currentPauseId != null

    override fun updateReloadedBreakpoint(breakpoint: Breakpoint) {
        packetSender.sendPacket(NetworkServerConnection.updateReloadedBreakpointPacketChannel, UpdateReloadedBreakpointS2CPacket(breakpoint, clientEditorDebugConnection).write())
    }

    override fun popStackFrames(stackFrames: Int) {
        packetSender.sendPacket(NetworkServerConnection.popStackFramesPacketChannel, PopStackFramesS2CPacket(stackFrames, clientEditorDebugConnection).write())
    }

    override fun pushStackFrames(stackFrames: List<MinecraftStackFrame>) {
        packetSender.sendPacket(NetworkServerConnection.pushStackFramesPacketChannel, PushStackFramesS2CPacket(stackFrames, clientEditorDebugConnection).write())
    }

    class PopStackFramesS2CPacket(val amount: Int, val editorDebugConnection: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readInt(), buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeInt(amount)
            buf.writeUuid(editorDebugConnection)
        }
    }
    class PushStackFramesS2CPacket(val stackFrames: List<MinecraftStackFrame>, val editorDebugConnection: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(
            List(buf.readVarInt()) { MinecraftStackFrame(buf) },
            buf.readUuid()
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeVarInt(stackFrames.size)
            for(frame in stackFrames) {
                frame.write(buf)
            }
            buf.writeUuid(editorDebugConnection)
        }
    }

    class PausedUpdateS2CPacket(val editorDebugConnection: UUID, val pause: Pair<UUID, StoppedEventArguments>?): ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(buf.readUuid(), buf.readNullable {
            buf.readUuid() to StoppedEventArguments().apply {
                reason = buf.readString()
                description = buf.readNullableString()
                allThreadsStopped = buf.readNullableBool()
                text = buf.readNullableString()
                hitBreakpointIds = buf.readNullable {
                    Array(buf.readVarInt()) {
                        buf.readVarInt()
                    }
                }
                preserveFocusHint = buf.readNullableBool()
                threadId = buf.readNullableInt()
            }
        })

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(editorDebugConnection)
            buf.writeNullable(pause) { pauseBuf, pauseValue ->
                pauseBuf.writeUuid(pauseValue.first)
                val args = pauseValue.second
                pauseBuf.writeString(args.reason)
                pauseBuf.writeNullableString(args.description)
                pauseBuf.writeNullableBool(args.allThreadsStopped)
                pauseBuf.writeNullableString(args.text)
                val hitBreakpointIds = args.hitBreakpointIds
                buf.writeNullable(hitBreakpointIds) { idBuf, breakpointIds ->
                    idBuf.writeVarInt(breakpointIds.size)
                    for (id in breakpointIds) {
                        idBuf.writeVarInt(id)
                    }
                }
                buf.writeNullableBool(args.preserveFocusHint)
                buf.writeNullableInt(args.threadId)
            }
        }
    }

    class UpdateReloadedBreakpointS2CPacket(val breakpoint: Breakpoint, val editorDebugConnection: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readBreakpoint(), buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeBreakpoint(breakpoint)
            buf.writeUuid(editorDebugConnection)
        }
    }
}