package net.papierkorb2292.command_crafter.parser.helper

interface ProcessedInputCursorMapperContainer {
    fun `command_crafter$getProcessedInputCursorMapper`(): ProcessedInputCursorMapper?
    fun `command_crafter$setProcessedInputCursorMapper`(mapper: ProcessedInputCursorMapper)
}