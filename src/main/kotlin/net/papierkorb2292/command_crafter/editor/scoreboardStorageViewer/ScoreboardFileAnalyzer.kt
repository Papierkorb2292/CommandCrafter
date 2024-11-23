package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler

object ScoreboardFileAnalyzer : FileAnalyseHandler {
    private const val ANALYZER_CONFIG_PATH = ".scoreboardStorage"
    override fun canHandle(file: OpenFile) =
        file.parsedUri.scheme == "scoreboardStorage"
                && file.parsedUri.path.startsWith("/scoreboards/")
                && file.parsedUri.path.endsWith(".json")

    override fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer): AnalyzingResult {
        val analyzingResult = StringRangeTreeJsonResourceAnalyzer.analyze(
            file,
            languageServer,
            ServerScoreboardStorageFileSystem.OBJECTIVE_CODEC
        )
        analyzingResult.clearDisabledFeatures(languageServer.featureConfig, listOf(
            StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX + ANALYZER_CONFIG_PATH,
            StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX,
            ""
        ))
        return analyzingResult
    }
}