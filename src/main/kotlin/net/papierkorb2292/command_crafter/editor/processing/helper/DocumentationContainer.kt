package net.papierkorb2292.command_crafter.editor.processing.helper

interface DocumentationContainer {
    fun `command_crafter$getDocumentation`(): String?
    fun `command_crafter$setDocumentation`(documentation: String)
}