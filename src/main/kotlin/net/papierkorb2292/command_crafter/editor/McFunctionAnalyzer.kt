package net.papierkorb2292.command_crafter.editor

import net.minecraft.command.CommandSource
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.Language
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.Position

class McFunctionAnalyzer(
    private val sourceProvider: (MinecraftLanguageServer) -> CommandSource,
    private val resultWrapper: ((AnalyzingResult) -> AnalyzingResult)? = null
) : FileAnalyseHandler {
    override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith(".mcfunction")

    override fun analyze(
        file: OpenFile,
        languageServer: MinecraftLanguageServer,
    ): AnalyzingResult {
        val dispatcher = languageServer.minecraftServer.commandDispatcher
        val reader = DirectiveStringReader(
            file.createFileMappingInfo(),
            dispatcher,
            AnalyzingResourceCreator(languageServer, file.uri).apply {
                (file.persistentAnalyzerData as? AnalyzingResourceCreator.CacheData)?.let { persistentCache ->
                    if(persistentCache.usedCommandDispatcher == dispatcher)
                        previousCache = persistentCache
                }
                newCache.usedCommandDispatcher = dispatcher
            }
        )
        val result = AnalyzingResult(reader.fileMappingInfo, Position())
        reader.resourceCreator.resourceStack.push(AnalyzingResourceCreator.ResourceStackEntry(result))
        LanguageManager.analyse(reader, sourceProvider(languageServer), result, Language.TopLevelClosure(VanillaLanguage()))
        reader.resourceCreator.resourceStack.pop()
        result.clearDisabledFeatures(languageServer.featureConfig, listOf(LanguageManager.ANALYZER_CONFIG_PATH, ""))
        if(!Thread.currentThread().isInterrupted)
            file.persistentAnalyzerData = reader.resourceCreator.newCache
        if(resultWrapper != null)
            return resultWrapper(result)
        return result
    }
}