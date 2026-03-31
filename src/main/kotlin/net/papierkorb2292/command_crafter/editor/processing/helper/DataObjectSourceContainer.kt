package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding

interface DataObjectSourceContainer {
    fun `command_crafter$setDataObjectSource`(dataObjectSource: DataObjectDecoding.DataObjectSource)
    fun `command_crafter$getDataObjectSource`(): DataObjectDecoding.DataObjectSource?
}