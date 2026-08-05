package net.papierkorb2292.command_crafter.editor

import org.eclipse.lsp4j.jsonrpc.debug.json.DebugMessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

class InitialMessageJsonHandler(supportedNormalMethods: Map<String, JsonRpcMethod>, supportedDebugMethods: Map<String, JsonRpcMethod>) : MessageJsonHandler(supportedNormalMethods, {
    // Make sure not to depend on `this` or and fields, because GSON is built before the constructor is done
    it.registerTypeAdapterFactory(
        InitialMessageTypeAdapter.Factory(
            MessageJsonHandler(supportedNormalMethods),
            DebugMessageJsonHandler(supportedDebugMethods)
        )
    )
})