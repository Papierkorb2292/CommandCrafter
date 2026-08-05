package net.papierkorb2292.command_crafter.editor

import com.google.gson.GsonBuilder
import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

class InitialMessageJsonHandler(supportedNormalMethods: Map<String, JsonRpcMethod>, private val supportedDebugMethods: Map<String, JsonRpcMethod>) : MessageJsonHandler(supportedNormalMethods) {

    override fun getDefaultGsonBuilder(): GsonBuilder {
        // DebugMessageJsonHandler can't be in a field, because this method is called before the constructor is done
        return super.defaultGsonBuilder
            .registerTypeAdapterFactory(
                InitialMessageTypeAdapter.Factory(
                    this,
                    DebugMessageJsonHandler(supportedDebugMethods)
                )
            )
    }
}