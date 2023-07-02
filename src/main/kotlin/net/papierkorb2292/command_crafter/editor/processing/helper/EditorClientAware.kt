package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.EditorClient

interface EditorClientAware {
    fun connect(client: EditorClient)
}