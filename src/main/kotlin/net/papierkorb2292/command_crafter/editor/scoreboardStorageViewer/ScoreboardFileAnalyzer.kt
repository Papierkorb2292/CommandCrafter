package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer

import net.papierkorb2292.command_crafter.editor.EditorDocument
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.StopInfo
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTreeJsonResourceAnalyzer

object ScoreboardFileAnalyzer : FileAnalyseHandler {
    private const val ANALYZER_CONFIG_PATH = ".scoreboardStorage"
    override fun canHandle(file: EditorDocument) =
        file.parsedUri.scheme == "scoreboardStorage"
                && file.parsedUri.path.startsWith("/scoreboards/")
                && file.parsedUri.path.endsWith(".json")

    override fun analyze(file: EditorDocument, languageServer: MinecraftLanguageServer, stopInfo: StopInfo?): AnalyzingResult {
        val analyzingResult = StringRangeTreeJsonResourceAnalyzer.analyze(
            file,
            languageServer,
            stopInfo,
            ServerScoreboardStorageFileSystem.OBJECTIVE_CODEC
        )
        return analyzingResult.filterDisabledFeatures(languageServer.featureConfig, listOf(
            StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX + ANALYZER_CONFIG_PATH,
            StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX,
            ""
        ))
    }
}