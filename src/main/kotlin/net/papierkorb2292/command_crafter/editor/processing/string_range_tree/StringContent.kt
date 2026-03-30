package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper

data class StringContent(val content: String, val cursorMapper: SplitProcessedInputCursorMapper, val escaper: StringEscaper) {
    fun interface StringContentGetter<TNode> {
        fun getStringContent(node: TNode): StringContent?
    }
}