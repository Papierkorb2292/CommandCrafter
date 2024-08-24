package net.papierkorb2292.command_crafter.editor.console

import net.papierkorb2292.command_crafter.helper.SizeLimitedCallbackLinkedBlockingQueue

interface Log {
    val name: String
    fun addMessageCallback(callback: SizeLimitedCallbackLinkedBlockingQueue.Callback<String>)
}