package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.EditorDocument
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.StopInfo
import net.papierkorb2292.command_crafter.editor.processing.symbols.ImmutableSymbolAccess

interface FileAnalyseHandler {
    fun canHandle(file: EditorDocument): Boolean
    fun analyze(
        file: EditorDocument,
        languageServer: MinecraftLanguageServer,
        documentSymbolAccess: ImmutableSymbolAccess,
        stopInfo: StopInfo?
    ): AnalyzingResult
    fun addSymbolTypes()
}