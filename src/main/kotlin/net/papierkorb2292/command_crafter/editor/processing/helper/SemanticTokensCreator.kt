package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder

interface SemanticTokensCreator {
    fun `command_crafter$setSemanticTokensBuilder`(builder: SemanticTokensBuilder, cursorOffset: Int)
}