package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.EditorDocument
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.StopInfo
import net.papierkorb2292.command_crafter.editor.processing.symbols.SymbolDocInfo

interface FileAnalyseHandler {
    fun canHandle(file: EditorDocument): Boolean
    fun analyze(
        file: EditorDocument,
        languageServer: MinecraftLanguageServer,
        stopInfo: StopInfo?,
        docInfo: SymbolDocInfo?
    ): AnalyzingResult
    fun addSymbolTypes()
}