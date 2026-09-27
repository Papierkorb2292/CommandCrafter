package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.EditorDocument
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

interface FileAnalyseHandler {
    fun canHandle(file: EditorDocument): Boolean
    fun analyze(file: EditorDocument, languageServer: MinecraftLanguageServer): AnalyzingResult {
        throw UnsupportedOperationException("This FileAnalyzeHandler doesn't have a synchronous analyze implementation")
    }
    fun analyzeAsync(
        file: EditorDocument,
        languageServer: MinecraftLanguageServer,
        executor: ExecutorService,
        completableFuture: CompletableFuture<AnalyzingResult>
    ): Future<*> {
        return executor.submit {
            completableFuture.complete(analyze(file, languageServer))
        }
    }
}