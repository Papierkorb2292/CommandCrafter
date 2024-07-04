package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resource.metadata.PackResourceMetadata
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler

object PackMetaAnalyzer : FileAnalyseHandler {
    private val NULL_PROVIDER = { _: Any? -> null }
    private val MERGED_DECODER: Decoder<Unit> = RecordCodecBuilder.create {
        it.group(
            PackResourceMetadata.CODEC.fieldOf(PackResourceMetadata.SERIALIZER.key)
                .forGetter(NULL_PROVIDER),
            //TODO: Add more serializers
        ).apply(it) { }
    }

    override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith("pack.mcmeta")

    override fun analyze(file: OpenFile, languageServer: MinecraftLanguageServer) =
        StringRangeTreeJsonResourceAnalyzer.analyze(file, languageServer, MERGED_DECODER)
}