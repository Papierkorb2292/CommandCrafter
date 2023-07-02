package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.console.Channel
import net.papierkorb2292.command_crafter.editor.console.ConsoleMessage
import net.papierkorb2292.command_crafter.editor.console.RemoveChannelNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient

interface EditorClient : LanguageClient {
    @JsonNotification
    fun createChannel(channel: Channel)

    @JsonNotification
    fun removeChannel(channel: RemoveChannelNotification)

    @JsonNotification
    fun updateChannel(channel: Channel)

    @JsonNotification
    fun logMinecraftMessage(message: ConsoleMessage)
}