package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree

interface StringRangeTreeCreator<TNode> {
    fun `command_crafter$setStringRangeTreeBuilder`(builder: StringRangeTree.Builder<TNode>)
}