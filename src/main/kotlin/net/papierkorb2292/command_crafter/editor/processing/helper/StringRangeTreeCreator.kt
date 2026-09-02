package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree

interface StringRangeTreeCreator<TNode: Any> {
    fun `command_crafter$setStringRangeTreeBuilder`(builder: StringRangeTree.Builder<TNode>)
}