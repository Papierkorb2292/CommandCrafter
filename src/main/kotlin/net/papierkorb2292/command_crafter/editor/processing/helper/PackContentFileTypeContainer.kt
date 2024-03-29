package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType

interface PackContentFileTypeContainer {
    fun `command_crafter$setPackContentFileType`(packContentFileType: PackContentFileType)
    fun `command_crafter$getPackContentFileType`(): PackContentFileType?
}