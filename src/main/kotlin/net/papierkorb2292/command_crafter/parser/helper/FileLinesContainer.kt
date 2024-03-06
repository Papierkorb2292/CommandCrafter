package net.papierkorb2292.command_crafter.parser.helper

interface FileLinesContainer {
    fun `command_crafter$setLines`(lines: List<String>)
    fun `command_crafter$getLines`(): List<String>?
}