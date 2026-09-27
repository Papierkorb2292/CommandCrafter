package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.server.packs.FeatureFlagsMetadataSection
import net.minecraft.server.packs.OverlayMetadataSection
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.resources.ResourceFilterSection
import net.papierkorb2292.command_crafter.editor.EditorDocument
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTreeJsonResourceAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTreeJsonResourceAnalyzer.Companion.codecFromMetaSection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

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

    override fun canHandle(file: EditorDocument) = file.parsedUri.path.endsWith("pack.mcmeta")

    override fun analyzeAsync(
        file: EditorDocument,
        languageServer: MinecraftLanguageServer,
        executor: ExecutorService,
        completableFuture: CompletableFuture<AnalyzingResult>
    ): Future<*> {
        val packType = file.workspacePackInfo?.packType ?: CompletableFuture.completedFuture(null)
        return packType.thenApplyAsync({ packType ->
            val decoder = when(packType) {
                PackContentFileType.PackType.DATA -> MERGED_DATAPACK_DECODER
                PackContentFileType.PackType.RESOURCE -> MERGED_RESOURCEPACK_DECODER
                else -> MERGED_UNKNOWN_DECODER
            }
            val analyzingResult = StringRangeTreeJsonResourceAnalyzer.analyze(
                file,
                languageServer,
                decoder
            )
            completableFuture.complete(analyzingResult.filterDisabledFeatures(
                languageServer.featureConfig, listOf(
                    StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX + ANALYZER_CONFIG_PATH,
                    StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX,
                    ""
                )
            ))
        }, executor)
    }
}