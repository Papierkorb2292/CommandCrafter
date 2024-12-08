package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resource.metadata.PackFeatureSetMetadata
import net.minecraft.resource.metadata.PackOverlaysMetadata
import net.minecraft.resource.metadata.PackResourceMetadata
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler

object PackMetaAnalyzer : FileAnalyseHandler {
    private val NULL_PROVIDER = { _: Any? -> null }
    private val MERGED_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            PackResourceMetadata.SERIALIZER.codec.fieldOf(PackResourceMetadata.SERIALIZER.name)
                .forGetter(NULL_PROVIDER),
            PackFeatureSetMetadata.SERIALIZER.codec.optionalFieldOf(PackFeatureSetMetadata.SERIALIZER.name)
                .forGetter(NULL_PROVIDER),
            PackOverlaysMetadata.SERIALIZER.codec.optionalFieldOf(PackOverlaysMetadata.SERIALIZER.name)
                .forGetter(NULL_PROVIDER)
        ).apply(it) { _, _, _ -> }
    }

    override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith("pack.mcmeta")

    override fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer) =
        StringRangeTreeJsonResourceAnalyzer.analyze(file, languageServer, MERGED_DECODER)
}