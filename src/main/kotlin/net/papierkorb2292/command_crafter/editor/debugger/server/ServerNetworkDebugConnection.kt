package net.papierkorb2292.command_crafter.editor.debugger.server

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.function.Macro
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.set

class ServerNetworkDebugConnection(
    player: ServerPlayerEntity,
    val clientEditorDebugConnection: UUID,
    override val oneTimeDebugTarget: EditorDebugConnection.DebugTarget? = null,
    override var nextSourceReference: Int = 1
) : NetworkIdentifiedDebugConnection {
    companion object {
        val outputGson = Gson()
    }

    override val networkHandler: ServerPlayNetworkHandler = player.networkHandler
    override val lifecycle = EditorDebugConnection.Lifecycle()

    var currentPauseId: UUID? = null
        private set

    private val packetSender = ServerPlayNetworking.getSender(player)
    private val playerName = player.name.string

    init {
        lifecycle.shouldExitEvent.thenAccept {
            packetSender.sendPacket(NetworkServerConnection.debuggerExitPacketChannel, DebuggerExitS2CPacket(it, clientEditorDebugConnection).write())
        }
        if(oneTimeDebugTarget != null) {
            val server = player.server
            val pauseContext = PauseContext(server, this, oneTimeDebugTarget.stopOnEntry)
            lifecycle.configurationDoneEvent.thenRunAsync({
                PauseContext.currentPauseContext.set(pauseContext)
                try {
                    runOneTimeDebugTarget(server, oneTimeDebugTarget)
                } catch (e: Throwable) {
                    if(e !is ExecutionPausedThrowable) throw e
                    PauseContext.wrapExecution(e)
                } finally {
                    PauseContext.resetPauseContext()
                }
            }, server)
        }
    }

    private fun runOneTimeDebugTarget(server: MinecraftServer, oneTimeDebugTarget: EditorDebugConnection.DebugTarget) {
        when(oneTimeDebugTarget.targetFileType) {
            PackContentFileType.FUNCTIONS_FILE_TYPE -> {
                val function = server.commandFunctionManager.getFunction(oneTimeDebugTarget.targetId)
                function.ifPresentOrElse({
                    if(it is Macro<*>) {
                        output(OutputEventArguments().apply {
                            category = OutputEventArgumentsCategory.IMPORTANT
                            output = "Functions with macros can't be run directly"
                        })
                        return@ifPresentOrElse
                    }
                    server.commandFunctionManager.execute(it, server.commandSource)
                }, {
                    output(OutputEventArguments().apply {
                        category = OutputEventArgumentsCategory.IMPORTANT
                        output = "Function '${oneTimeDebugTarget.targetId}' not found"
                    })
                })
            }
            else -> output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.IMPORTANT
                output = "Tried to run unsupported debug target type: ${oneTimeDebugTarget.targetFileType}"
            })
        }
    }

    override fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer) {
        val pauseId = NetworkServerConnection.addServerDebugPause(DebugPauseInformation(actions, variables, clientEditorDebugConnection))
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

    override fun output(args: OutputEventArguments) {
        packetSender.sendPacket(NetworkServerConnection.debuggerOutputPacketChannel, DebuggerOutputS2CPacket(args, clientEditorDebugConnection).write())
    }

    override fun onSourceReferenceAdded() {
        nextSourceReference++
        packetSender.sendPacket(NetworkServerConnection.sourceReferenceAddedPacketChannel, SourceReferenceAddedS2CPacket(clientEditorDebugConnection).write())
    }

    override fun toString(): String {
        return "ServerNetworkDebugConnection(player=${playerName})"
    }

    /*fun prepareOneTimeFunctionDebug(function: CommandFunction<ServerCommandSource>, pauseOnEntry: Boolean) {
        preparedOneTimeDebugPauseContext = PauseContext(server, this, pauseOnEntry)
        lifecycle.configurationDoneEvent.thenRunAsync({
            PauseContext.currentPauseContext.set(preparedOneTimeDebugPauseContext)
            try {
                server.commandFunctionManager.execute(function, server.commandSource)
            } catch (e: Throwable) {
                if(e !is ExecutionPausedThrowable) throw e
                PauseContext.wrapExecution(e)
            } finally {
                PauseContext.resetPauseContext()
            }
        }, server)
    }*/

    class DebugPauseInformation(val actions: DebugPauseActions, val pauseContext: VariablesReferencer, val clientEditorDebugConnection: UUID)

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

    class DebuggerOutputS2CPacket(val args: OutputEventArguments, val editorDebugConnection: UUID) : ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(OutputEventArguments().apply {
            category = buf.readNullableString()
            output = buf.readString()
            group = buf.readNullableEnumConstant(OutputEventArgumentsGroup::class.java)
            variablesReference = buf.readNullableVarInt()
            source = buf.readNullable { buf.readSource() }
            line = buf.readNullableVarInt()
            column = buf.readNullableVarInt()
            data = outputGson.fromJson(buf.readString(), JsonElement::class.java)
        }, buf.readUuid())

        override fun write(buf: PacketByteBuf) {
            buf.writeNullableString(args.category)
            buf.writeString(args.output)
            buf.writeNullable(args.group, PacketByteBuf::writeEnumConstant)
            buf.writeNullableVarInt(args.variablesReference)
            buf.writeNullable(args.source, PacketByteBuf::writeSource)
            buf.writeNullableVarInt(args.line)
            buf.writeNullableVarInt(args.column)
            buf.writeString(outputGson.toJson(args.data))
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

    class ConfigurationDoneC2SPacket(val debugConnectionId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(debugConnectionId)
        }
    }

    class DebuggerExitS2CPacket(val args: ExitedEventArguments, val editorDebugConnection: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(
            ExitedEventArguments().apply { exitCode = buf.readVarInt() },
            buf.readUuid()
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeVarInt(args.exitCode)
            buf.writeUuid(editorDebugConnection)
        }
    }

    class DebugConnectionRegistrationC2SPacket(val oneTimeDebugTarget: EditorDebugConnection.DebugTarget?, val nextSourceReference: Int, val debugConnectionId: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readNullable {
            EditorDebugConnection.DebugTarget(
                buf.readEnumConstant(PackContentFileType::class.java),
                buf.readIdentifier(),
                buf.readBoolean()
            )
        }, buf.readVarInt(), buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeNullable(oneTimeDebugTarget) { targetBuf, target ->
                targetBuf.writeEnumConstant(target.targetFileType)
                targetBuf.writeIdentifier(target.targetId)
                targetBuf.writeBoolean(target.stopOnEntry)
            }
            buf.writeVarInt(nextSourceReference)
            buf.writeUuid(debugConnectionId)
        }
    }

    class SourceReferenceAddedS2CPacket(val editorDebugConnection: UUID): ByteBufWritable {
        constructor(buf: PacketByteBuf): this(buf.readUuid())
        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(editorDebugConnection)
        }
    }
}
