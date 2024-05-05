package net.papierkorb2292.command_crafter.editor.processing.helper

interface AnalyzingResultDataContainer {
    fun `command_crafter$setAnalyzingResult`(result: AnalyzingResult?)
    fun `command_crafter$getAnalyzingResult`(): AnalyzingResult?
}