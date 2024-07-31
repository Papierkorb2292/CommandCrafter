package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.util.Identifier

interface FileSourceContainer {
    fun `command_crafter$setFileSource`(lines: List<String>, fileId: Identifier)
    fun `command_crafter$getFileSourceLines`(): List<String>?
    fun `command_crafter$getFileSourceId`(): Identifier?
}