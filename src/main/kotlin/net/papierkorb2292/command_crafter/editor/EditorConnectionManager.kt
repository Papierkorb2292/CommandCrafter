package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.helper.CallbackExecutorService
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.*

class EditorConnectionManager(
    private val connectionAcceptor: EditorConnectionAcceptor,
    minecraftServerConnection: MinecraftServerConnection,
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
                val editorConnection = connectionAcceptor.accept() ?: return@Thread
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
                        String::class.java
                    )
                )
            )
        )
        connectorMessageReader.listen {
            if(it !is DebugRequestMessage) {
                return@listen
            }
            if(it.method != "connectToService") {
                return@listen
            }
            val serviceName = it.params as String
            val serviceCreator = serviceLaunchers[serviceName]
            if(serviceCreator != null) {
                connectorMessageReader.close()
                startService(editorConnection, serviceCreator)
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

    private fun startService(connection: EditorConnection, serviceLauncher: ServiceLauncher) {
        val serviceRemover = ServiceRemover(runningServices, null)
        val launchedService = serviceLauncher.launch(
            minecraftServerConnection,
            connection,
            CallbackExecutorService(
                Executors.newCachedThreadPool(),
                serviceRemover
            )
        )
        serviceRemover.service = launchedService.server
        runningServices[launchedService.server] = launchedService.client to launchedService.process
    }

    fun showMessage(message: MessageParams) {
        for((serviceClient, _) in runningServices.values) {
            (serviceClient.client as? LanguageClient ?: continue).showMessage(message)
        }
    }

    interface ServiceLauncher {
        fun launch(serverConnection: MinecraftServerConnection, editorConnection: EditorConnection, executorService: ExecutorService): LaunchedService
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
}