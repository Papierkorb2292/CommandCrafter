package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.helper.CallbackExecutorService
import net.papierkorb2292.command_crafter.helper.getType
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.*

class EditorConnectionManager(
    private val connectionAcceptor: EditorConnectionAcceptor,
    minecraftServerConnection: MinecraftServerConnection,
    val minecraftClientConnection: MinecraftClientConnection?,
    private val serviceLaunchers: Map<String, ServiceLauncher>
) {

    private val runningServices: ConcurrentMap<EditorService, Pair<ServiceClient, Future<Void>>> = ConcurrentHashMap()
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

    private fun handleConnection(editorConnection: EditorConnection) {
        val connectorMessageReader = StreamMessageProducer(
            editorConnection.inputStream,
            DebugMessageJsonHandler(
                mapOf(
                    "connectToService" to JsonRpcMethod.notification(
                        "connectToService",
                        getType<Either<String, ConnectToServiceArgs>>()
                    )
                )
            )
        )
        connectorMessageReader.listen { msg ->
            if(msg !is DebugRequestMessage) {
                return@listen
            }
            if(msg.method != "connectToService") {
                return@listen
            }
            @Suppress("UNCHECKED_CAST")
            val serviceArgs = msg.params as Either<String, ConnectToServiceArgs>
            val serviceName = serviceArgs.map({ it }, { it.service })
            val editorInfo = createEditorInfo(serviceArgs.right)
            val serviceCreator = serviceLaunchers[serviceName]
            if(serviceCreator != null) {
                connectorMessageReader.close()
                startService(editorConnection, serviceCreator, editorInfo)
            }
        }
    }

    fun stopServer() {
        connector?.interrupt()
        connector = null
        for((_, connection) in runningServices.values) {
            connection.cancel(true)
        }
        runningServices.clear()
        connectionAcceptor.stop()
    }

    private fun startService(connection: EditorConnection, serviceLauncher: ServiceLauncher, editorInfo: EditorInfo) {
        val serviceRemover = ServiceRemover(runningServices, null)
        val launchedService = serviceLauncher.launch(
            minecraftServerConnection,
            minecraftClientConnection,
            connection,
            CallbackExecutorService(
                Executors.newCachedThreadPool(),
                serviceRemover
            ),
            editorInfo
        )
        serviceRemover.service = launchedService.server
        runningServices[launchedService.server] = launchedService.client to launchedService.process
    }

    fun showMessage(message: MessageParams) {
        for((serviceClient, _) in runningServices.values) {
            (serviceClient.client as? LanguageClient ?: continue).showMessage(message)
        }
    }

    fun leave() {
        for(editorServer in runningServices.keys) {
            editorServer.leave()
        }
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
        fun launch(serverConnection: MinecraftServerConnection, clientConnection: MinecraftClientConnection?, editorConnection: EditorConnection, executorService: ExecutorService, editorInfo: EditorInfo): LaunchedService
    }

    class ServiceClient(val client: Any)

    class LaunchedService(
        val server: EditorService,
        val client: ServiceClient,
        val process: Future<Void>
    )

    class ServiceRemover(private val runningServices: MutableMap<EditorService, *>, var service: EditorService?) : () -> Unit {
        override fun invoke() {
            service?.onClosed()
            runningServices.remove(service)
        }
    }

    class ConnectToServiceArgs(var service: String, var featureConfig: Map<String, FeatureConfig.Entry>?, var extensionVersion: String?){
        constructor() : this("", null, null)
    }

    data class EditorInfo(val featureConfig: FeatureConfig, val extensionVersion: String?)
}