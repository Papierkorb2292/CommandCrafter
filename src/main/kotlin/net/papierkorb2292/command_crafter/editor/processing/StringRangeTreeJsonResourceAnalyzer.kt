package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.string_range_gson.Strictness
import org.eclipse.lsp4j.Position
import java.io.IOException
import java.io.StringReader

class StringRangeTreeJsonResourceAnalyzer(private val packContentFileType: PackContentFileType, private val fileDecoder: Decoder<*>, private val analyzerConfigPath: String) : FileAnalyseHandler {
    override fun canHandle(file: OpenFile) =
        PackContentFileType.parsePath(file.parsedUri.path)?.type == packContentFileType
                && (file.parsedUri.path.endsWith(".json") || file.parsedUri.path.endsWith(".mcmeta"))

    override fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer): AnalyzingResult {
        val analyzingResult = Companion.analyze(file, languageServer, fileDecoder)
        analyzingResult.clearDisabledFeatures(languageServer.featureConfig, listOf(
            JSON_ANALYZER_CONFIG_PATH_PREFIX + analyzerConfigPath,
            JSON_ANALYZER_CONFIG_PATH_PREFIX,
            ""
        ))
        return analyzingResult
    }

    companion object {
        const val JSON_ANALYZER_CONFIG_PATH_PREFIX = ".json"
        fun addJsonAnalyzer(packContentFileType: PackContentFileType, codec: Codec<*>, analyzerConfigPath: String = packContentFileType.contentTypePath) {
            MinecraftLanguageServer.addAnalyzer(StringRangeTreeJsonResourceAnalyzer(packContentFileType, codec, ".$analyzerConfigPath"))
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
            treeOperations.analyzeFull(result, languageServer, false, fileDecoder)
            treeOperations.generateDiagnostics(result, fileDecoder)
            return result
        }
    }
}