package net.papierkorb2292.command_crafter.editor

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.helper.WrappingExecutorService
import net.papierkorb2292.command_crafter.helper.getType
import net.papierkorb2292.command_crafter.mixin.editor.lsp4j.ConcurrentMessageProcessorAccessor
import net.papierkorb2292.command_crafter.mixin.editor.lsp4j.StandardLauncherAccessor
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.*

class EditorConnectionManager(
    private val connectionAcceptor: EditorConnectionAcceptor,
    minecraftServerConnection: MinecraftServerConnection,
    val minecraftClientConnection: MinecraftClientConnection?,
    private val serviceLaunchers: Map<String, ServiceLauncher>
) {
    companion object {
        fun injectInitialMessage(launcher: Launcher<*>, message: Message) {
            if(launcher !is StandardLauncherAccessor) {
                throw IllegalArgumentException("Expected LSP4J Launcher to be a StandardLauncher to inject message")
            }
            (launcher.msgProcessor as ConcurrentMessageProcessorAccessor).messageConsumer.consume(message)
        }
    }

    private val runningServices: ConcurrentMap<EditorService, RunningService> = ConcurrentHashMap()
    private var connector: Thread? = null

    var minecraftServerConnection: MinecraftServerConnection = minecraftServerConnection
        set(value) {
            field = value
            for(server in runningServices.keys) {
                server.setMinecraftServerConnection(value)
            }
        }

    fun startServer() {
        stopServer()
        connectionAcceptor.start()
        connector = Thread {
            while(connectionAcceptor.isRunning()) {
                val editorConnection = connectionAcceptor.accept() ?: continue
                try {
                    handleConnection(editorConnection)
                } catch(e: Exception) {
                    editorConnection.close()
                    CommandCrafter.LOGGER.error("Error while connecting to editor", e)
                }
            }
        }.apply { start() }
    }

    /**
     * When a new connection is established, we first need to figure out which service to connect to.
     * The client can either send a `connectToService` message or immediately send the initialization message
     * from which we can automatically determine the correct service.
     *
     * Note that LSP and DAP use different method formats, so both have to be tried. The `connectToService` message also uses the DAP format.
     */
    private fun handleConnection(editorConnection: EditorConnection) {
        val connectorMessageReader = StreamMessageProducer(
            editorConnection.inputStream,
            InitialMessageJsonHandler(
                mapOf(
                    "initialize" to JsonRpcMethod.request(
                        "initialize",
                        getType<InitializeResult>(),
                        getType<InitializeParams>()
                    ),
                ),
                mapOf(
                    "connectToService" to JsonRpcMethod.notification(
                        "connectToService",
                        getType<Either<String, ConnectToServiceArgs>>()
                    ),
                    "initialize" to JsonRpcMethod.request(
                        "initialize",
                        getType<Capabilities>(),
                        getType<InitializeRequestArguments>()
                    ),
                )
            )
        )
        connectorMessageReader.listen { msg ->
            if(msg is DebugRequestMessage && msg.method == "connectToService") {
                @Suppress("UNCHECKED_CAST")
                val serviceArgs = msg.params as Either<String, ConnectToServiceArgs>
                val serviceName = serviceArgs.map({ it }, { it.service })
                val editorInfo = createEditorInfo(serviceArgs.right)
                val serviceCreator = serviceLaunchers[serviceName]
                if(serviceCreator != null) {
                    connectorMessageReader.close()
                    startService(editorConnection, serviceCreator, editorInfo = editorInfo)
                }
                return@listen
            }

            // Check which service accepts this as initial message
            for((_, service) in serviceLaunchers) {
                if(service.isInitialMessage(msg)) {
                    connectorMessageReader.close()
                    startService(editorConnection, service, initialMessage = msg)
                    return@listen
                }
            }
        }
    }

    fun stopServer() {
        connector?.interrupt()
        connector = null
        for(runningService in runningServices.values) {
            runningService.process.cancel(true)
            runningService.connection.close()
        }
        runningServices.clear()
        connectionAcceptor.stop()
    }

    private fun startService(connection: EditorConnection, serviceLauncher: ServiceLauncher, editorInfo: EditorInfo? = null, initialMessage: Message? = null) {
        val serviceRemover = ServiceRemover(runningServices, null, Executors.newCachedThreadPool())
        val launchedService = serviceLauncher.launch(
            minecraftServerConnection,
            minecraftClientConnection,
            connection,
            WrappingExecutorService.withFinishedCallback(
                serviceRemover.threadPool,
                serviceRemover
            ),
            editorInfo,
            initialMessage
        )
        serviceRemover.service = launchedService.server
        runningServices[launchedService.server] = RunningService(launchedService.client, launchedService.process, connection)
    }

    fun showMessage(message: MessageParams) {
        for(runningService in runningServices.values) {
            (runningService.client.client as? LanguageClient ?: continue).showMessage(message)
        }
    }

    fun leave() {
        for(editorService in runningServices.keys) {
            editorService.leave()
        }
        stopServer()
    }

    fun copyForNewConnectionAcceptor(newConnectionAcceptor: EditorConnectionAcceptor): EditorConnectionManager {
        return EditorConnectionManager(
            newConnectionAcceptor,
            minecraftServerConnection,
            minecraftClientConnection,
            serviceLaunchers
        )
    }

    fun createEditorInfo(args: ConnectToServiceArgs?): EditorInfo {
        return EditorInfo(
            FeatureConfig(args?.featureConfig ?: emptyMap()),
            args?.extensionVersion
        )
    }

    interface ServiceLauncher {
        fun launch(
            serverConnection: MinecraftServerConnection,
            clientConnection: MinecraftClientConnection?,
            editorConnection: EditorConnection,
            executorService: ExecutorService,
            editorInfo: EditorInfo?,
            initialMessage: Message?,
        ): LaunchedService

        fun isInitialMessage(message: Message): Boolean = false
    }

    class ServiceClient(val client: Any)

    class LaunchedService(
        val server: EditorService,
        val client: ServiceClient,
        val process: Future<Void>
    )

    class RunningService(
        val client: ServiceClient,
        val process: Future<Void>,
        val connection: EditorConnection
    )

    class ServiceRemover(private val runningServices: MutableMap<EditorService, *>, var service: EditorService?, val threadPool: ExecutorService) : () -> Unit {
        override fun invoke() {
            service?.onClosed()
            runningServices.remove(service)
            threadPool.shutdown()
        }
    }

    /**
     * The connection arguments send by the editor when connecting to a service. Note that the featureConfig and extensionVersion are not really used anymore,
     * because the language server and the debug adapter sync those separately.
     */
    class ConnectToServiceArgs(var service: String, var featureConfig: Map<String, FeatureConfig.Entry>?, var extensionVersion: String?){
        constructor() : this("", null, null)
    }

    data class EditorInfo(val featureConfig: FeatureConfig, val extensionVersion: String?) {
        companion object {
            val DEFAULT = EditorInfo(FeatureConfig.EMPTY, null)
            val CODEC = RecordCodecBuilder.create<EditorInfo> { instance ->
                instance.group(
                    FeatureConfig.CODEC.optionalFieldOf("featureConfig", FeatureConfig.EMPTY).forGetter { it.featureConfig },
                    Codec.STRING.optionalFieldOf("extensionVersion", null).forGetter { it.extensionVersion }
                ).apply(instance, ::EditorInfo)
            }.optionalFieldOf("editorInfo", DEFAULT).codec()
        }

        fun withFeatureConfig(featureConfig: FeatureConfig) = copy(featureConfig = featureConfig)
    }
}