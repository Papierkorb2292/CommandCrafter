package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile

interface FileAnalyseHandler {
    fun canHandle(file: OpenFile): Boolean
    fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer): AnalyzingResult
}