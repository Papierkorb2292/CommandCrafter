package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.helper.memoizeLast

object FileTypeDispatchingAnalyzer : FileAnalyseHandler {
    val analyzers = mutableMapOf<PackContentFileType, FileAnalyseHandler>()

    private val analyzerGetter = { file: OpenFile ->
        analyzers[PackContentFileType.parsePath(file.parsedUri.path)?.type]
    }.memoizeLast()

    override fun canHandle(file: OpenFile) = analyzerGetter(file)?.canHandle(file) ?: false

    override fun analyzeAsync(
        file: OpenFile,
        languageServer: MinecraftLanguageServer
    ) = analyzerGetter(file)!!.analyzeAsync(file, languageServer)
}