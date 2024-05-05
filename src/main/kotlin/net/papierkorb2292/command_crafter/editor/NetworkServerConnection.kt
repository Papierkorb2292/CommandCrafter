package net.papierkorb2292.command_crafter.editor

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Maps
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestions
import io.netty.channel.local.LocalChannel
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter
import net.papierkorb2292.command_crafter.editor.console.CommandExecutor
import net.papierkorb2292.command_crafter.editor.console.Log
import net.papierkorb2292.command_crafter.editor.console.PreLaunchLogListener
import net.papierkorb2292.command_crafter.editor.debugger.ServerDebugConnectionService
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkDebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.client.NetworkVariablesReferencer
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.ReservedBreakpointIdStart
import net.papierkorb2292.command_crafter.editor.debugger.helper.getDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.helper.setupOneTimeDebugTarget
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.UnparsedServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.ContextCompletionProvider
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer
import net.papierkorb2292.command_crafter.helper.CallbackLinkedBlockingQueue
import net.papierkorb2292.command_crafter.mixin.editor.ClientConnectionAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.ClientCommandSourceAccessor
import net.papierkorb2292.command_crafter.networking.packets.*
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.set

class NetworkServerConnection private constructor(private val client: MinecraftClient, initializePacket: InitializeNetworkServerConnectionS2CPacket) : MinecraftServerConnection {
    companion object {
        const val SERVER_LOG_CHANNEL = "server"

        val currentGetVariablesRequests: MutableMap<UUID, CompletableFuture<Array<Variable>>> = mutableMapOf()
        val currentSetVariableRequests: MutableMap<UUID, CompletableFuture<VariablesReferencer.SetVariableResult?>> = mutableMapOf()
        val currentStepInTargetsRequests: MutableMap<UUID, CompletableFuture<StepInTargetsResponse>> = mutableMapOf()
        val currentSourceReferenceRequests: MutableMap<UUID, CompletableFuture<SourceResponse?>> = mutableMapOf()
        val currentBreakpointIdsRequests: MutableMap<UUID, CompletableFuture<ReservedBreakpointIdStart>> = mutableMapOf()

        private var currentConnectionRequest: Pair<UUID, CompletableFuture<NetworkServerConnection>>? = null
        private val currentBreakpointRequests: MutableMap<UUID, (Array<Breakpoint>) -> Unit> = Maps.newHashMap()

        private val clientEditorDebugConnections: BiMap<EditorDebugConnection, UUID> = HashBiMap.create()
        private val serverEditorDebugConnections: MutableMap<UUID, ServerNetworkDebugConnection> = mutableMapOf()
        private val serverDebugPauses: MutableMap<UUID, ServerNetworkDebugConnection.DebugPauseInformation> = mutableMapOf()

        fun requestAndCreate(): CompletableFuture<NetworkServerConnection> {
            if(!ClientPlayNetworking.canSend(RequestNetworkServerConnectionC2SPacket.ID)) {
                return CompletableFuture.failedFuture(ServerConnectionNotSupportedException("Server doesn't support editor connections"))
            }
            val requestId = UUID.randomUUID()
            val result = CompletableFuture<NetworkServerConnection>()
            currentConnectionRequest = requestId to result
            ClientPlayNetworking.send(RequestNetworkServerConnectionC2SPacket(requestId))
            return result
        }

        fun registerClientPacketHandlers() {
            ClientPlayNetworking.registerGlobalReceiver(InitializeNetworkServerConnectionS2CPacket.ID) handler@{ payload, context ->
                val currentRequest = currentConnectionRequest ?: return@handler
                if(payload.requestId != currentRequest.first) return@handler
                currentRequest.second.complete(NetworkServerConnection(context.client(), payload))
            }
            ClientPlayNetworking.registerGlobalReceiver(LogMessageS2CPacket.ID) handler@{ payload, _ ->
                val serverConnection = ClientCommandCrafter.editorConnectionManager.minecraftServerConnection
                if(serverConnection is NetworkServerConnection) {
                    serverConnection.serverLog?.run { log.add(payload.logMessage) }
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(SetBreakpointsResponseS2CPacket.ID) { payload, _ ->
                currentBreakpointRequests.remove(payload.requestId)?.invoke(payload.breakpoints)
            }
            ClientPlayNetworking.registerGlobalReceiver(PopStackFramesS2CPacket.ID) { payload, _ ->
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.popStackFrames(payload.amount)
            }
            ClientPlayNetworking.registerGlobalReceiver(PushStackFramesS2CPacket.ID) { payload, _ ->
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.pushStackFrames(payload.stackFrames)
            }
            ClientPlayNetworking.registerGlobalReceiver(PausedUpdateS2CPacket.ID) handler@{ payload, context ->
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@handler
                val pause = payload.pause
                if(pause == null) editorConnection.pauseEnded()
                else editorConnection.pauseStarted(
                    NetworkDebugPauseActions(context.responseSender(), pause.first),
                    pause.second,
                    NetworkVariablesReferencer(context.responseSender(), pause.first)
                )
            }
            ClientPlayNetworking.registerGlobalReceiver(UpdateReloadedBreakpointS2CPacket.ID) { payload, _ ->
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.updateReloadedBreakpoint(payload.update)
            }
            ClientPlayNetworking.registerGlobalReceiver(GetVariablesResponseS2CPacket.ID) { payload, _ ->
                currentGetVariablesRequests.remove(payload.requestId)?.complete(payload.variables)
            }
            ClientPlayNetworking.registerGlobalReceiver(SetVariableResponseS2CPacket.ID) { payload, _ ->
                currentSetVariableRequests.remove(payload.requestId)?.complete(payload.response)
            }
            ClientPlayNetworking.registerGlobalReceiver(StepInTargetsResponseS2CPacket.ID) { payload, _ ->
                currentStepInTargetsRequests.remove(payload.requestId)?.complete(payload.response)
            }
            ClientPlayNetworking.registerGlobalReceiver(DebuggerOutputS2CPacket.ID) { payload, _ ->
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.output(payload.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(SourceReferenceResponseS2CPacket.ID) { payload, _ ->
                currentSourceReferenceRequests.remove(payload.requestId)?.complete(payload.source)
            }
            ClientPlayNetworking.registerGlobalReceiver(ReserveBreakpointIdsRequestS2CPacket.ID) { payload, context ->
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.reserveBreakpointIds(payload.count)?.thenAccept {
                    context.responseSender().sendPacket(
                        ReserveBreakpointIdsResponseC2SPacket(it, payload.requestId)
                    )
                }
            }
            ClientPlayNetworking.registerGlobalReceiver(DebuggerExitS2CPacket.ID) { payload, _ ->
                val editorConnection = clientEditorDebugConnections.inverse()[payload.editorDebugConnection] ?: return@registerGlobalReceiver
                editorConnection.lifecycle.shouldExitEvent.complete(payload.args)
            }
            ClientPlayNetworking.registerGlobalReceiver(SourceReferenceAddedS2CPacket.ID) { payload, _ ->
                clientEditorDebugConnections.inverse()[payload.editorDebugConnection]?.onSourceReferenceAdded()
            }
            ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
                clientEditorDebugConnections.clear()
            }
        }

        fun registerServerPacketHandlers() {
            ServerPlayNetworking.registerGlobalReceiver(RequestNetworkServerConnectionC2SPacket.ID) handler@{ payload, context ->
                //TODO: Whitelist for which players are allowed to connect this way
                sendConnectionRequestResponse(context.player().server, payload, context.responseSender())

                if(context.player().server.isDedicated) {
                    startSendingLogMessages(context.responseSender(), context.player())
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(SetBreakpointsRequestC2SPacket.ID) { payload, context ->
                val server = context.player().server
                val debugManager = server.getDebugManager()
                server.execute {
                    context.responseSender().sendPacket(
                        SetBreakpointsResponseS2CPacket(debugManager.setBreakpoints(
                            payload.breakpoints,
                            payload.fileType,
                            payload.id,
                            context.player(),
                            serverEditorDebugConnections.computeIfAbsent(payload.debugConnectionId) {
                                ServerNetworkDebugConnection(context.player(), it)
                            },
                            payload.sourceReference
                        ), payload.requestId)
                    )
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(EditorDebugConnectionRemovedC2SPacket.ID) { payload, context ->
                val debugConnection = serverEditorDebugConnections.remove(payload.debugConnectionId) ?: return@registerGlobalReceiver
                debugConnection.currentPauseId?.run {
                    serverDebugPauses.remove(this)?.actions?.continue_()
                }
                val server = context.player().server
                val debugManager = server.getDebugManager()
                server.execute { debugManager.removeDebugConnection(debugConnection) }
            }
            ServerPlayNetworking.registerGlobalReceiver(DebugPauseActionC2SPacket.ID) { payload, _ ->
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerGlobalReceiver
                payload.action.apply(debugPause.actions, payload)
            }
            ServerPlayNetworking.registerGlobalReceiver(GetVariablesRequestC2SPacket.ID) { payload, context ->
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerGlobalReceiver
                context.player().server.execute {
                    debugPause.pauseContext.getVariables(payload.args).thenAccept {
                        context.responseSender().sendPacket(
                            GetVariablesResponseS2CPacket(payload.requestId, it)
                        )
                    }
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(SetVariableRequestC2SPacket.ID) { payload, context ->
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerGlobalReceiver
                context.player().server.execute {
                    debugPause.pauseContext.setVariable(payload.args).thenAccept {
                        context.responseSender().sendPacket(
                            SetVariableResponseS2CPacket(payload.requestId, it)
                        )
                    }
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(StepInTargetsRequestC2SPacket.ID) { payload, context ->
                val debugPause = serverDebugPauses[payload.pauseId] ?: return@registerGlobalReceiver
                context.player().server.execute {
                    debugPause.actions.stepInTargets(payload.frameId).thenAccept {
                        context.responseSender().sendPacket(
                            StepInTargetsResponseS2CPacket(payload.requestId, it)
                        )
                    }
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(SourceReferenceRequestC2SPacket.ID) { payload, context ->
                val debugConnection = serverEditorDebugConnections[payload.debugConnectionId] ?: return@registerGlobalReceiver
                val server = context.player().server
                val debugManager = server.getDebugManager()
                server.execute {
                    val sourceResponse = debugManager.retrieveSourceReference(debugConnection, payload.sourceReference)
                    context.responseSender().sendPacket(
                        SourceReferenceResponseS2CPacket(sourceResponse, payload.requestId)
                    )
                }
            }
            ServerPlayNetworking.registerGlobalReceiver(ReserveBreakpointIdsResponseC2SPacket.ID) { payload, _ ->
                currentBreakpointIdsRequests.remove(payload.requestId)?.complete(payload.start)
            }
            ServerPlayNetworking.registerGlobalReceiver(ConfigurationDoneC2SPacket.ID) { payload, _ ->
                val debugConnection = serverEditorDebugConnections[payload.debugConnectionId] ?: return@registerGlobalReceiver
                debugConnection.lifecycle.configurationDoneEvent.complete(null)
            }
            ServerPlayNetworking.registerGlobalReceiver(DebugConnectionRegistrationC2SPacket.ID) { payload, context->
                val debugConnection = ServerNetworkDebugConnection(context.player(), payload.debugConnectionId, payload.oneTimeDebugTarget, payload.nextSourceReference, payload.suspendServer)
                serverEditorDebugConnections.putIfAbsent(payload.debugConnectionId, debugConnection)
                debugConnection.setupOneTimeDebugTarget(context.player().server)
            }
            ServerPlayNetworking.registerGlobalReceiver(ContextCompletionRequestC2SPacket.ID) { payload, context ->
                val server = context.player().server
                @Suppress("UNCHECKED_CAST")
                val reader = DirectiveStringReader(payload.inputLines, server.commandManager.dispatcher as CommandDispatcher<CommandSource>, AnalyzingResourceCreator(null, ""))
                server.execute {
                    val analyzingResult = AnalyzingResult(reader, Position())
                    LanguageManager.analyse(reader, server.commandSource, analyzingResult, LanguageManager.DEFAULT_CLOSURE)
                    val completionFuture = analyzingResult.getCompletionProviderForCursor(payload.cursor)
                        ?.dataProvider?.invoke(payload.cursor)
                        ?: CompletableFuture.completedFuture(listOf())
                    completionFuture.thenAccept {
                        val suggestions = Suggestions(StringRange.at(0), emptyList())
                        @Suppress("KotlinConstantConditions")
                        (suggestions as CompletionItemsContainer).`command_crafter$setCompletionItem`(it)
                        context.responseSender().sendPacket(CommandSuggestionsS2CPacket(payload.completionId, suggestions))
                    }
                }
            }

            ServerPlayConnectionEvents.DISCONNECT.register { networkHandler, server -> server.execute {
                serverEditorDebugConnections.values.removeIf {
                    if(it.networkHandler == networkHandler) {
                        server.getDebugManager().removeDebugConnection(it)
                        serverDebugPauses[it.currentPauseId]?.actions?.continue_()
                        return@removeIf true
                    }
                    return@removeIf false
                }
            }}
        }

        private fun startSendingLogMessages(
            packetSender: PacketSender,
            player: ServerPlayerEntity,
        ) {
            PreLaunchLogListener.addLogListener(object : CallbackLinkedBlockingQueue.Callback<String> {
                override fun onElementAdded(e: String) {
                    packetSender.sendPacket(LogMessageS2CPacket(e))
                }

                override fun shouldRemoveCallback() = player.isDisconnected
            })
        }

        private fun sendConnectionRequestResponse(
            server: MinecraftServer,
            requestPacket: RequestNetworkServerConnectionC2SPacket,
            packetSender: PacketSender,
        ) {
            val rootCommandNode = limitCommandTreeForSource(server.commandManager, ServerCommandSource(
                CommandOutput.DUMMY,
                Vec3d.ZERO,
                Vec2f.ZERO,
                null,
                server.functionPermissionLevel,
                "",
                ScreenTexts.EMPTY,
                null,
                null
            ))

            val responsePacket = InitializeNetworkServerConnectionS2CPacket(
                CommandTreeS2CPacket(rootCommandNode),
                server.functionPermissionLevel,
                requestPacket.requestId,
            )

            try {
                IdArgumentTypeAnalyzer.shouldAddPackContentFileType.set(true)
                packetSender.sendPacket(responsePacket)
            } finally {
                IdArgumentTypeAnalyzer.shouldAddPackContentFileType.remove()
            }
        }

        fun addServerDebugPause(debugPause: ServerNetworkDebugConnection.DebugPauseInformation): UUID {
            val id = UUID.randomUUID()
            serverDebugPauses[id] = debugPause
            return id
        }
        fun removeServerDebugPauseHandler(id: UUID) {
            serverDebugPauses.remove(id)
        }
    }

    override val commandDispatcher = CommandDispatcher(initializePacket.commandTree.getCommandTree(CommandRegistryAccess.of(client.networkHandler?.registryManager, client.networkHandler?.enabledFeatures)))
    override val functionPermissionLevel = initializePacket.functionPermissionLevel
    override val serverLog =
        if(client.networkHandler?.run { (connection as ClientConnectionAccessor).channel } is LocalChannel) {
            null
        } else {
            NetworkServerLog()
        }
    override val commandExecutor = NetworkCommandExecutor(client)
    override val debugService = object : ServerDebugConnectionService {
        override fun setBreakpoints(
            breakpoints: Array<UnparsedServerBreakpoint>,
            source: Source,
            fileType: PackContentFileType,
            id: Identifier,
            editorDebugConnection: EditorDebugConnection
        ): CompletableFuture<SetBreakpointsResponse> {
            val debugConnectionId = getOrCreateDebugConnectionId(editorDebugConnection)
            val completableFuture = CompletableFuture<SetBreakpointsResponse>()
            val requestId = UUID.randomUUID()
            currentBreakpointRequests[requestId] = {
                for(breakpoint in it) {
                    if(breakpoint.source == null) {
                        breakpoint.source = source
                    }
                }
                val setBreakpointsDebuggerResponse = SetBreakpointsResponse()
                setBreakpointsDebuggerResponse.breakpoints = it
                completableFuture.complete(setBreakpointsDebuggerResponse)
            }
            ClientPlayNetworking.send(
                SetBreakpointsRequestC2SPacket(breakpoints, fileType, id, source.sourceReference, requestId, debugConnectionId)
            )
            return completableFuture
        }

        override fun retrieveSourceReference(sourceReference: Int, editorDebugConnection: EditorDebugConnection): CompletableFuture<SourceResponse?> {
            val debugConnectionId = getOrCreateDebugConnectionId(editorDebugConnection)
            val completableFuture = CompletableFuture<SourceResponse?>()
            val requestId = UUID.randomUUID()
            currentSourceReferenceRequests[requestId] = completableFuture
            ClientPlayNetworking.send(
                SourceReferenceRequestC2SPacket(sourceReference, requestId, debugConnectionId)
            )
            return completableFuture
        }

        override fun removeEditorDebugConnection(editorDebugConnection: EditorDebugConnection) {
            val id = clientEditorDebugConnections.remove(editorDebugConnection) ?: return
            ClientPlayNetworking.send(
                EditorDebugConnectionRemovedC2SPacket(id)
            )
        }

        private fun getOrCreateDebugConnectionId(editorDebugConnection: EditorDebugConnection): UUID {
            return clientEditorDebugConnections.computeIfAbsent(editorDebugConnection) {
                val id = UUID.randomUUID()
                ClientPlayNetworking.send(
                    DebugConnectionRegistrationC2SPacket(it.oneTimeDebugTarget, it.nextSourceReference, it.suspendServer, id)
                )
                it.lifecycle.configurationDoneEvent.thenRun {
                    ClientPlayNetworking.send(
                        ConfigurationDoneC2SPacket(id)
                    )
                }
                id
            }
        }
    }
    override val contextCompletionProvider = object : ContextCompletionProvider {
        override fun getCompletions(context: CommandContext<*>, fullInput: DirectiveStringReader<AnalyzingResourceCreator>): CompletableFuture<Suggestions> {
            val source = client.networkHandler!!.commandSource as ClientCommandSourceAccessor
            val completionId = ++source.completionId
            val result = CompletableFuture<Suggestions>()
            source.setPendingCommandCompletion(result)
            ClientPlayNetworking.send(
                ContextCompletionRequestC2SPacket(completionId, fullInput.lines, fullInput.absoluteCursor)
            )
            return result
        }
    }

    class NetworkServerLog : Log {
        val log = CallbackLinkedBlockingQueue<String>()
        override val name
            get() = SERVER_LOG_CHANNEL
        override fun addMessageCallback(callback: CallbackLinkedBlockingQueue.Callback<String>) {
            log.addCallback(callback)
        }
    }

    class NetworkCommandExecutor(private val client: MinecraftClient) : CommandExecutor {
        override fun executeCommand(command: String) {
            client.inGameHud.chatHud.addToMessageHistory("/$command") //TODO: Test how multiple lines work in chat screen
            client.networkHandler?.sendChatCommand(command)
        }
    }

    class ServerConnectionNotSupportedException(message: String?) : Exception(message) {
        constructor() : this(null)
    }
}
