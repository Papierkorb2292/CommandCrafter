package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.commands.functions.StringTemplate

interface MacroCursorMapperProvider {
    fun `command_crafter$getCursorMapper`(arguments: List<String>): SplitProcessedInputCursorMapper
}

@Suppress("CAST_NEVER_SUCCEEDS")
fun StringTemplate.getCursorMapper(arguments: List<String>): SplitProcessedInputCursorMapper =
    (this as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(arguments)