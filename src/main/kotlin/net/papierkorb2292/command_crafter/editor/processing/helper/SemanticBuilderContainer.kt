package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder

interface SemanticBuilderContainer {
    fun `command_crafter$setSemanticTokensBuilder`(builder: SemanticTokensBuilder, cursorOffset: Int)
    fun `command_crafter$getSemanticTokensBuilder`(): SemanticTokensBuilder?
    fun `command_crafter$getCursorOffset`(): Int
}