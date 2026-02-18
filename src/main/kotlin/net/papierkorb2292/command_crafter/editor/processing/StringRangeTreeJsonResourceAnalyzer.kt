package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.metadata.MetadataSectionType
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
        return analyzingResult.filterDisabledFeatures(languageServer.featureConfig, listOf(
            JSON_ANALYZER_CONFIG_PATH_PREFIX + analyzerConfigPath,
            JSON_ANALYZER_CONFIG_PATH_PREFIX,
            ""
        ))
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
            StringRangeTree.TreeOperations.forJson(
                parsedStringRangeTree,
                concatenatedLines
            ).withRegistry(languageServer.dynamicRegistryManager)
                .analyzeFull(result, fileDecoder)
            return result
        }

        private val NULL_PROVIDER = { _: Any? -> null }
        fun codecFromMetaSection(section: MetadataSectionType<*>, optional: Boolean): RecordCodecBuilder<Unit, *> =
            if(optional) section.codec.optionalFieldOf(section.name).forGetter(NULL_PROVIDER)
            else section.codec.fieldOf(section.name).forGetter(NULL_PROVIDER)
    }
}