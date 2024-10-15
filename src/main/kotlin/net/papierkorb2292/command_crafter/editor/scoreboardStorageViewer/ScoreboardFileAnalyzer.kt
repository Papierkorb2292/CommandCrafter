package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler

object ScoreboardFileAnalyzer : FileAnalyseHandler {
    override fun canHandle(file: OpenFile) =
        file.parsedUri.scheme == "scoreboardStorage"
                && file.parsedUri.path.startsWith("/scoreboards/")
                && file.parsedUri.path.endsWith(".json")

    override fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer) =
        StringRangeTreeJsonResourceAnalyzer.analyze(file, languageServer, ServerScoreboardStorageFileSystem.OBJECTIVE_CODEC)
}