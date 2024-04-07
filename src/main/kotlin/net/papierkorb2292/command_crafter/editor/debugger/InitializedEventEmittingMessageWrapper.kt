package net.papierkorb2292.command_crafter.editor.debugger

import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import java.util.function.Function

/**
 * A message wrapper that emits the initialized event when the initialize request is received.
 *
 * This is necessary, because the initialized event must be sent after the response to the initialize
 * request, but the initialize request handler ([MinecraftDebuggerServer.initialize]) can only send
 * requests before the response is sent, not afterward.
 */
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