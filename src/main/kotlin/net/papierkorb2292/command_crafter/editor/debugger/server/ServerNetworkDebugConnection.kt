package net.papierkorb2292.command_crafter.editor.debugger.server

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.StoppedEventArguments
import java.util.*
import java.util.concurrent.CompletableFuture

class ServerNetworkDebugConnection(val player: ServerPlayerEntity, val clientEditorDebugConnection: UUID) : EditorDebugConnection {
    private var currentPauseId: UUID? = null
    private val packetSender = ServerPlayNetworking.getSender(player)

    override fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer) {
        val pauseId = NetworkServerConnection.addServerDebugPause(DebugPauseInformation(actions, variables, player))
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

    override fun updateReloadedBreakpoint(update: BreakpointEventArguments) {
        packetSender.sendPacket(NetworkServerConnection.updateReloadedBreakpointPacketChannel, UpdateReloadedBreakpointS2CPacket(update, clientEditorDebugConnection).write())
    }

    override fun reserveBreakpointIds(count: Int): CompletableFuture<ReservedBreakpointIdStart> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<ReservedBreakpointIdStart>()
        packetSender.sendPacket(NetworkServerConnection.reserveBreakpointIdsRequestPacketChannel, ReserveBreakpointIdsRequestS2CPacket(count, clientEditorDebugConnection, requestId).write())
        NetworkServerConnection.currentBreakpointIdsRequests[requestId] = future
        return future
    }

    override fun popStackFrames(stackFrames: Int) {
        packetSender.sendPacket(NetworkServerConnection.popStackFramesPacketChannel, PopStackFramesS2CPacket(stackFrames, clientEditorDebugConnection).write())
    }

    override fun pushStackFrames(stackFrames: List<MinecraftStackFrame>) {
        packetSender.sendPacket(NetworkServerConnection.pushStackFramesPacketChannel, PushStackFramesS2CPacket(stackFrames, clientEditorDebugConnection).write())
    }

    override fun onPauseLocationSkipped() {
        packetSender.sendPacket(NetworkServerConnection.debuggerPauseLocationSkippedPacketChannel, PauseLocationSkippedS2CPacket(clientEditorDebugConnection).write())
    }

    class DebugPauseInformation(val actions: DebugPauseActions, val pauseContext: VariablesReferencer, val player: ServerPlayerEntity)

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

    class UpdateReloadedBreakpointS2CPacket(val update: BreakpointEventArguments, val editorDebugConnection: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(BreakpointEventArguments().apply {
            breakpoint = buf.readBreakpoint()
            reason = buf.readString()
        }, buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeBreakpoint(update.breakpoint)
            buf.writeString(update.reason)
            buf.writeUuid(editorDebugConnection)
        }
    }

    class PauseLocationSkippedS2CPacket(val editorDebugConnection: UUID) : ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(editorDebugConnection)
        }
    }

    class ReserveBreakpointIdsRequestS2CPacket(val count: Int, val editorDebugConnection: UUID, val requestId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readInt(), buf.readUuid(), buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeInt(count)
            buf.writeUuid(editorDebugConnection)
            buf.writeUuid(requestId)
        }
    }

    class ReserveBreakpointIdsResponseC2SPacket(val start: ReservedBreakpointIdStart, val requestId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readInt(), buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeInt(start)
            buf.writeUuid(requestId)
        }
    }
}