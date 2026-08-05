package net.papierkorb2292.command_crafter.editor

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import org.eclipse.lsp4j.jsonrpc.debug.adapters.DebugMessageTypeAdapter
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.adapters.MessageTypeAdapter
import org.eclipse.lsp4j.jsonrpc.messages.Message
import java.io.IOException
import java.io.StringReader

/**
 * An initial message type adapter that first reads the whole JSON tree and then
 * tries parsing it with both the normal MessageTypeAdapter and the DebugMessageTypeAdapter.
 */
class InitialMessageTypeAdapter(
    normalHandler: MessageJsonHandler,
    debugHandler: MessageJsonHandler,
    private val gson: Gson,
) : MessageTypeAdapter(normalHandler, gson) {

    private val debugDelegate = DebugMessageTypeAdapter(debugHandler, gson)

    class Factory(private val normalHandler: MessageJsonHandler, private val debugHandler: MessageJsonHandler) : TypeAdapterFactory {
        override fun <T> create(gson: Gson, typeToken: TypeToken<T>): TypeAdapter<T>? {
            if (!Message::class.java.isAssignableFrom(typeToken.rawType)) return null
            @Suppress("UNCHECKED_CAST")
            return InitialMessageTypeAdapter(normalHandler, debugHandler, gson) as TypeAdapter<T>
        }
    }

    @Throws(IOException::class)
    override fun read(`in`: JsonReader): Message? {
        // Read the full JSON element, so it can be parsed multiple times
        val element = JsonParser.parseReader(`in`)
        val json = gson.toJson(element)

        // Try the base MessageTypeAdapter (super.read)
        return try {
            super.read(JsonReader(StringReader(json)))
        } catch (firstEx: Exception) {
            // Otherwise try the DebugMessageTypeAdapter
            try {
                debugDelegate.read(JsonReader(StringReader(json)))
            } catch (secondEx: Exception) {
                val ex = JsonParseException(
                    "Failed to parse message with both MessageTypeAdapter and DebugMessageTypeAdapter",
                    firstEx
                )
                ex.addSuppressed(secondEx)
                throw ex
            }
        }
    }
}