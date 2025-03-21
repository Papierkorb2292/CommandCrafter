package net.papierkorb2292.command_crafter.editor.processing.helper

interface StringIdentifiableNameTransformerConsumer {
    fun `command_crafter$setNameTransformer`(nameTransformer: (String) -> String)
}