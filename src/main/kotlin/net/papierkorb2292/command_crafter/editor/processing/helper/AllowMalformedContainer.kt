package net.papierkorb2292.command_crafter.editor.processing.helper

interface AllowMalformedContainer {
    fun `command_crafter$setAllowMalformed`(allowMalformed: Boolean)
    fun `command_crafter$getAllowMalformed`(): Boolean
}