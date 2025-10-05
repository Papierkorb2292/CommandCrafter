package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resource.ResourceType
import net.minecraft.resource.metadata.PackFeatureSetMetadata
import net.minecraft.resource.metadata.PackOverlaysMetadata
import net.minecraft.resource.metadata.PackResourceMetadata
import net.minecraft.resource.metadata.ResourceMetadataSerializer
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import java.util.concurrent.CompletableFuture
import kotlin.io.path.Path

object PackMetaAnalyzer : FileAnalyseHandler {
    private const val ANALYZER_CONFIG_PATH = ".packmeta"
    private val NULL_PROVIDER = { _: Any? -> null }
    private val MERGED_DATAPACK_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            toRootCodec(PackResourceMetadata.getSerializerFor(ResourceType.SERVER_DATA), false),
            toRootCodec(PackFeatureSetMetadata.SERIALIZER, true),
            toRootCodec(PackOverlaysMetadata.getSerializerFor(ResourceType.SERVER_DATA), true)
        ).apply(it) { _, _, _ -> }
    }
    private val MERGED_RESOURCEPACK_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            toRootCodec(PackResourceMetadata.getSerializerFor(ResourceType.CLIENT_RESOURCES), false),
            toRootCodec(PackFeatureSetMetadata.SERIALIZER, true),
            toRootCodec(PackOverlaysMetadata.getSerializerFor(ResourceType.CLIENT_RESOURCES), true)
        ).apply(it) { _, _, _ -> }
    }
    private val MERGED_UNKNOWN_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            toRootCodec(PackResourceMetadata.DESCRIPTION_SERIALIZER, false),
            toRootCodec(PackFeatureSetMetadata.SERIALIZER, true)
        ).apply(it) { _, _ -> }
    }

    override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith("pack.mcmeta")

    override fun analyzeAsync(file: OpenFile, languageServer: MinecraftLanguageServer): CompletableFuture<AnalyzingResult> {
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
            }
            .thenApply { decoder ->
                val analyzingResult = StringRangeTreeJsonResourceAnalyzer.analyze(
                    file,
                    languageServer,
                    decoder
                )
                analyzingResult.clearDisabledFeatures(languageServer.featureConfig, listOf(
                    StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX + ANALYZER_CONFIG_PATH,
                    StringRangeTreeJsonResourceAnalyzer.JSON_ANALYZER_CONFIG_PATH_PREFIX,
                    ""
                ))
                analyzingResult
            }
    }

    private fun toRootCodec(serializer: ResourceMetadataSerializer<*>, optional: Boolean): RecordCodecBuilder<Unit, *> =
        if(optional) serializer.codec.optionalFieldOf(serializer.name).forGetter(NULL_PROVIDER)
        else serializer.codec.fieldOf(serializer.name).forGetter(NULL_PROVIDER)
}