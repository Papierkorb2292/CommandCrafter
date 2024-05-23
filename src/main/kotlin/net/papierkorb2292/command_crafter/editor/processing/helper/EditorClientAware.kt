package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.CommandCrafterLanguageClient

interface EditorClientAware {
    fun connect(client: CommandCrafterLanguageClient)
}