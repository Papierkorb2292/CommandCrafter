package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.StopInfo
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.symbols.SymbolTypeInfo
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.Language
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.Position

class McFunctionAnalyzer(
    private val resultWrapper: ((MinecraftLanguageServer, AnalyzingResult) -> AnalyzingResult)? = null
) : FileAnalyseHandler {
    val ANALYZER_CONFIG_PATH = ".mcfunction"

    override fun canHandle(file: EditorDocument) = file.parsedUri.path.endsWith(".mcfunction")

    override fun analyze(
        file: EditorDocument,
        languageServer: MinecraftLanguageServer,
        stopInfo: StopInfo?,
    ): AnalyzingResult {
        val source = CommandCrafter.analyzingSourceProvider(languageServer)
        val dispatcher = languageServer.minecraftServer.commandDispatcher
        val mappingInfo = file.createFileMappingInfo()
        val reader = DirectiveStringReader(
            mappingInfo,
            dispatcher,
            AnalyzingResourceCreator(
                languageServer,
                file.uri,
                languageServer.dynamicRegistryManager,
                source,
                mappingInfo,
                stopInfo
            ).apply {
                loadCache(file, dispatcher)
            }
        )
        DataObjectDecoding.BUILTIN_REGISTRY_OVERRIDE.runWithValueSwap(languageServer.dynamicRegistryManager) {
            VanillaLanguage.IS_ANALYZING_COMMANDS.runWithValueSwap(true) {
                var result = AnalyzingResourceCreator.tryAnalyseOnlyMacroModification(reader)
                if(result == null) {
                    // No cache hit, parse function instead
                    result = AnalyzingResult(reader.fileMappingInfo, Position())
                    reader.resourceCreator.resourceStack.push(AnalyzingResourceCreator.ResourceStackEntry(result))
                    LanguageManager.analyse(
                        reader,
                        source,
                        result,
                        Language.TopLevelClosure(VanillaLanguage())
                    )
                    reader.resourceCreator.resourceStack.pop()
                    reader.resourceCreator.storeCache(file, result)
                    result = reader.resourceCreator.overlayMacros(result)
                } else {
                    // There is no new outermost analyzing result for the cache, since only a macro was changed
                    reader.resourceCreator.storeCacheKeepAnalyzingResult(file)
                }
                result = result.filterDisabledFeatures(languageServer.featureConfig, listOf(ANALYZER_CONFIG_PATH, ""))
                if(resultWrapper != null)
                    return resultWrapper(languageServer, result)
                return result
            }
        }
    }

    override fun addSymbolTypes() {
        MinecraftLanguageServer.symbolTypes[PackContentFileType.FUNCTIONS_FILE_TYPE] = SymbolTypeInfo("mcfunction", true)
    }
}