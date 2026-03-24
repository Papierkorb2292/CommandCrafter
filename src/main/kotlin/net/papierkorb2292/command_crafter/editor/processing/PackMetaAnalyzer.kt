package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.server.packs.FeatureFlagsMetadataSection
import net.minecraft.server.packs.OverlayMetadataSection
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.resources.ResourceFilterSection
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer.Companion.codecFromMetaSection
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import kotlin.io.path.Path

class PackMetaAnalyzer(clientsideLanguageMetadataSection: MetadataSectionType<*>?) : FileAnalyseHandler {
    private val ANALYZER_CONFIG_PATH = ".packmeta"
    private val MERGED_DATAPACK_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            codecFromMetaSection(PackMetadataSection.forPackType(PackType.SERVER_DATA), false),
            codecFromMetaSection(FeatureFlagsMetadataSection.TYPE, true),
            codecFromMetaSection(OverlayMetadataSection.forPackType(PackType.SERVER_DATA), true),
            codecFromMetaSection(ResourceFilterSection.TYPE, true),
        ).apply(it) { _, _, _, _ -> }
    }
    private val MERGED_RESOURCEPACK_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            codecFromMetaSection(PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES), false),
            codecFromMetaSection(FeatureFlagsMetadataSection.TYPE, true),
            codecFromMetaSection(OverlayMetadataSection.forPackType(PackType.CLIENT_RESOURCES), true),
            codecFromMetaSection(ResourceFilterSection.TYPE, true),
            if(clientsideLanguageMetadataSection != null) // Passed as parameter, because it's not available on dedicated servers
                    codecFromMetaSection(clientsideLanguageMetadataSection, true)
                else RecordCodecBuilder.point<Unit, Unit>(Unit)
        ).apply(it) { _, _, _, _, _ -> }
    }
    private val MERGED_UNKNOWN_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            codecFromMetaSection(PackMetadataSection.FALLBACK_TYPE, false),
            codecFromMetaSection(FeatureFlagsMetadataSection.TYPE, true)
        ).apply(it) { _, _ -> }
    }

    override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith("pack.mcmeta")

    override fun analyzeAsync(file: OpenFile, languageServer: MinecraftLanguageServer, executor: ExecutorService, completableFuture: CompletableFuture<AnalyzingResult>): Future<*> {
        val packFolder = Path(file.parsedUri.path).parent
        val dataPath = file.parsedUri.copyWithPath(packFolder.resolve("data").toString()).toString()
        val assetsPath = file.parsedUri.copyWithPath(packFolder.resolve("assets").toString()).toString()
        return languageServer.client!!.fileExists(dataPath)
            .thenCombine(languageServer.client!!.fileExists(assetsPath)) { dataFolderExists, assetsFolderExists ->
                if(!dataFolderExists.xor(assetsFolderExists))
                    MERGED_UNKNOWN_DECODER // There is either both a data folder and an assets folder or neither, so the type of the pack can't be determined
                else if(dataFolderExists)
                    MERGED_DATAPACK_DECODER
                else
                    MERGED_RESOURCEPACK_DECODER
            }.thenApplyAsync({ decoder ->
                val analyzingResult = StringRangeTreeJsonResourceAnalyzer.analyze(
                    file,
                    languageServer,
                    decoder
                )
                analyzingResult.clearDisabledFeatures(
                    languageServer.featureConfig, listOf(
                        StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX + ANALYZER_CONFIG_PATH,
                        StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX,
                        ""
                    )
                )
                completableFuture.complete(analyzingResult)
            }, executor)
    }
}