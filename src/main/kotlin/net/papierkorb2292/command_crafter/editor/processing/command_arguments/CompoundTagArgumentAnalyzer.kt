package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Decoder
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.CompoundTagArgument
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.DiagnosticSeverity

class CompoundTagArgumentAnalyzer : CommandArgumentAnalyzerService<CompoundTagArgument> {
    override val argumentTypes
        get() = listOf(CompoundTagArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: CompoundTagArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        val dataObjectSource = (type as DataObjectSourceContainer).`command_crafter$getDataObjectSource`()
        val nbtReader = TagParser.create(NbtOps.INSTANCE)
        (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        (nbtReader as AnalyzingResultCreator).`command_crafter$setAnalyzingResult`(result)
        val treeBuilder = StringRangeTree.Builder<Tag>()
        @Suppress("UNCHECKED_CAST")
        (nbtReader as StringRangeTreeCreator<Tag>).`command_crafter$setStringRangeTreeBuilder`(treeBuilder)
        val nbt: Tag = nbtReader.parseAsArgument(reader)
        val tree: StringRangeTree<Tag> = treeBuilder.build(nbt)

        val decoder: Decoder<*>? =
            if(dataObjectSource != null)
                DataObjectDecoding.getForReader(reader)?.getDecoderForSource(dataObjectSource, context)
            else null

        StringRangeTree.TreeOperations.forNbt(
            tree,
            reader
        ).withDiagnosticSeverity(DiagnosticSeverity.Warning)
            .analyzeFull(result, true, decoder)
    }
}