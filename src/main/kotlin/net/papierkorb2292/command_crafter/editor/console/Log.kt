package net.papierkorb2292.command_crafter.editor.console

import net.papierkorb2292.command_crafter.helper.CallbackLinkedBlockingQueue

interface Log {
    val name: String
    fun addMessageCallback(callback: CallbackLinkedBlockingQueue.Callback<String>)
}