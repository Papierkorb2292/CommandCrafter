package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.EditorDocument
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.editor.processing.symbols.ImmutableSymbolAccess

object FileTypeDispatchingAnalyzer : FileAnalyseHandler {
    val analyzers = mutableMapOf<PackContentFileType, FileAnalyseHandler>()

    private fun getAnalyzer(file: EditorDocument) = analyzers[file.typedId?.type]

    override fun canHandle(file: EditorDocument) = getAnalyzer(file)?.canHandle(file) ?: false

    override fun analyze(
        file: EditorDocument,
        languageServer: MinecraftLanguageServer,
        documentSymbolAccess: ImmutableSymbolAccess,
        stopInfo: StopInfo?
    ): AnalyzingResult {
        return getAnalyzer(file)!!.analyze(file, languageServer,, stopInfo)
    }

    override fun addSymbolTypes() {
        for(analyzer in analyzers.values) {
            analyzer.addSymbolTypes()
        }
    }
}