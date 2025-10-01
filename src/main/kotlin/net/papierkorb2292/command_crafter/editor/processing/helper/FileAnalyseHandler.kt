package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import java.util.concurrent.CompletableFuture

interface FileAnalyseHandler {
    fun canHandle(file: OpenFile): Boolean
    fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer): AnalyzingResult {
        throw UnsupportedOperationException("This FileAnalyzeHandler doesn't have a synchronous analyze implementation")
    }
    fun analyzeAsync(file: OpenFile, languageServer: MinecraftLanguageServer): CompletableFuture<AnalyzingResult> {
        return CompletableFuture.supplyAsync { analyze(file, languageServer) }
    }
}