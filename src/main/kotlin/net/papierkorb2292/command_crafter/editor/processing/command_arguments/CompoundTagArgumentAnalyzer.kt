package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Decoder
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.CompoundTagArgument
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.TreeOperations
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.DiagnosticSeverity

class CompoundTagArgumentAnalyzer : CommandArgumentAnalyzerService<CompoundTagArgument> {
    companion object {
        fun analyzeReader(reader: StringReader, result: AnalyzingResult, registries: RegistryAccess?, treeOperationsBuilder: (StringRangeTree<Tag>) -> TreeOperations<Tag>, branchBehaviorProvider: BranchBehaviorProvider<Tag>?, decoder: Decoder<*>?) {
            val nbtReader = TagParser.create(NbtOps.INSTANCE)
            (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
            (nbtReader as AnalyzingResultCreator).`command_crafter$setAnalyzingResult`(result)
            val treeBuilder = StringRangeTree.Builder<Tag>()
            @Suppress("UNCHECKED_CAST")
            (nbtReader as StringRangeTreeCreator<Tag>).`command_crafter$setStringRangeTreeBuilder`(treeBuilder)
            val nbt: Tag = nbtReader.parseAsArgument(reader)
            if(decoder != null) {
                val tree: StringRangeTree<Tag> = treeBuilder.build(nbt)
                treeOperationsBuilder(tree)
                    .withDiagnosticSeverity(DiagnosticSeverity.Warning)
                    .withBranchBehaviorProvider(branchBehaviorProvider ?: BranchBehaviorProvider.Decode)
                    .withRegistry(registries)
                    .analyzeFull(result, decoder)
            }
        }
    }

    override val argumentTypes
        get() = listOf(CompoundTagArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: CompoundTagArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        val dataObjectSource = (type as DataObjectSourceContainer).`command_crafter$getDataObjectSource`()
        val decoder = if(dataObjectSource != null) DataObjectDecoding.getForReader(reader).getDecoderForSource(dataObjectSource, context, reader) else null
        analyzeReader(
            reader,
            result,
            reader.resourceCreator.registries,
            { tree -> TreeOperations.forNbt(tree, reader) },
            dataObjectSource?.getNBTBranchBehavior(),
            decoder
        )
    }
}