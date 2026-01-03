package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.string_range_gson.Strictness
import org.eclipse.lsp4j.Position
import java.io.IOException
import java.io.StringReader
import kotlin.jvm.optionals.getOrNull

class StringRangeTreeJsonResourceAnalyzer(private val packContentFileType: PackContentFileType, private val fileDecoder: Decoder<*>, private val analyzerConfigPath: String) : FileAnalyseHandler {
    override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith(".json") || file.parsedUri.path.endsWith(".mcmeta")

    override fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer): AnalyzingResult {
        val contentTypeFilePath = packContentFileType.contentTypePath
        val tagPrefix = "tags/"
        val tagRegistry = if(contentTypeFilePath.startsWith(tagPrefix)) {
            languageServer.dynamicRegistryManager
                .lookup(ResourceKey.createRegistryKey(Identifier.withDefaultNamespace(contentTypeFilePath.substring(tagPrefix.length))))
                .getOrNull()
        } else null
        val analyzingResult = if(tagRegistry != null) {
            CURRENT_TAG_ANALYZING_REGISTRY.runWithValue(tagRegistry) {
                Companion.analyze(file, languageServer, fileDecoder)
            }
        } else Companion.analyze(file, languageServer, fileDecoder)
        analyzingResult.clearDisabledFeatures(languageServer.featureConfig, listOf(
            JSON_ANALYZER_CONFIG_PATH_PREFIX + analyzerConfigPath,
            JSON_ANALYZER_CONFIG_PATH_PREFIX,
            ""
        ))
        return analyzingResult
    }

    companion object {
        const val JSON_ANALYZER_CONFIG_PATH_PREFIX = ".json"
        val CURRENT_TAG_ANALYZING_REGISTRY = ThreadLocal<HolderLookup.RegistryLookup<*>>()

        fun addJsonAnalyzers(resourceTypes: Map<PackContentFileType, Codec<*>>) {
            FileTypeDispatchingAnalyzer.analyzers += resourceTypes.mapValues { entry ->
                StringRangeTreeJsonResourceAnalyzer(entry.key, entry.value, ".${entry.key.contentTypePath}")
            }
        }

        fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer, fileDecoder: Decoder<*>): AnalyzingResult {
            val lines = file.stringifyLines()
            val result = AnalyzingResult(FileMappingInfo(lines), Position())
            val concatenatedLines = lines.joinToString("\n")
            val reader = StringReader(concatenatedLines)
            val parsedStringRangeTree = try {
                StringRangeTreeJsonReader(reader).read(Strictness.LENIENT, true)
            } catch(e: IOException) {
                return result
            }
            val treeOperations = StringRangeTree.TreeOperations.forJson(
                parsedStringRangeTree,
                concatenatedLines
            ).withRegistry(languageServer.dynamicRegistryManager)
                // JSON highlighting should be provided by the editor, but `shouldGenerateSemanticTokens` is set to `true` to add semantic tokens from analyzed strings, so the semantic token provider needs to be set to a dummy instance to not interfere with highlighting of other elements
                .copy(semanticTokenProvider = DummySemanticTokenProvider)
            treeOperations.analyzeFull(result, true, fileDecoder)
            return result
        }
    }
}