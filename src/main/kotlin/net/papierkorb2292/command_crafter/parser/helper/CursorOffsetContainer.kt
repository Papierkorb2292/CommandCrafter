package net.papierkorb2292.command_crafter.parser.helper

interface CursorOffsetContainer {
    fun `command_crafter$setCursorOffset`(readCharacters: Int, skippedChars: Int)
    fun `command_crafter$getReadCharacters`(): Int
    fun `command_crafter$getSkippedChars`(): Int
}

fun CursorOffsetContainer.getCursorOffset() = `command_crafter$getReadCharacters`() - `command_crafter$getSkippedChars`()