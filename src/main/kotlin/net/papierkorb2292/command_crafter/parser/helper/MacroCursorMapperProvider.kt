package net.papierkorb2292.command_crafter.parser.helper

interface MacroCursorMapperProvider {
    fun `command_crafter$getCursorMapper`(arguments: List<String>): SplitProcessedInputCursorMapper
}