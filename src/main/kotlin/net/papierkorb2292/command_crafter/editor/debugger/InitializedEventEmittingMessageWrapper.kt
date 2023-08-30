package net.papierkorb2292.command_crafter.editor.debugger

import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import java.util.function.Function

class InitializedEventEmittingMessageWrapper : Function<MessageConsumer, MessageConsumer> {
    var client: IDebugProtocolClient? = null

    override fun apply(originalConsumer: MessageConsumer): MessageConsumer {
        return MessageConsumer {
            originalConsumer.consume(it)
            if (it is DebugResponseMessage && it.method == "initialize") {
                client?.initialized()
            }
        }
    }
}