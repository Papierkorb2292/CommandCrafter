package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType

interface FileSourceContainer {
    fun `command_crafter$setFileSource`(lines: List<String>, fileId: Identifier, fileType: PackContentFileType)
    fun `command_crafter$getFileSourceLines`(): List<String>?
    fun `command_crafter$getFileSourceId`(): Identifier?
    fun `command_crafter$getFileSourceType`(): PackContentFileType?
}