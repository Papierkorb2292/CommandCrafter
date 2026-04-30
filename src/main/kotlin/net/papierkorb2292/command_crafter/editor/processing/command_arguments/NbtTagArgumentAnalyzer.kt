package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.NbtTagArgument
import net.minecraft.nbt.*
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.TreeOperations
import net.papierkorb2292.command_crafter.mixin.CommandContextAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.DiagnosticSeverity

class NbtTagArgumentAnalyzer : CommandArgumentAnalyzerService<NbtTagArgument> {
    override val argumentTypes
        get() = listOf(NbtTagArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: NbtTagArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        val nbtReader = TagParser.create(NbtOps.INSTANCE)
        (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        val treeBuilder = StringRangeTree.Builder<Tag>()
        @Suppress("UNCHECKED_CAST")
        (nbtReader as StringRangeTreeCreator<Tag>).`command_crafter$setStringRangeTreeBuilder`(treeBuilder)
        (nbtReader as AnalyzingResultCreator).`command_crafter$setAnalyzingResult`(result)
        val nbt = nbtReader.parseAsArgument(reader)
        val originalTree: StringRangeTree<Tag> = treeBuilder.build(nbt)

        val dataObjectSource = (type as DataObjectSourceContainer).`command_crafter$getDataObjectSource`()
        val isPath = dataObjectSource?.isPathReference() ?: false
        if(isPath) {
            val pathArgument = (context as CommandContextAccessor).arguments[dataObjectSource.argumentName]
            if(pathArgument != null) {
                val pathInput = pathArgument.range.get(context.input)
                val path = NbtPathArgumentAnalyzer.readNbtPath(StringReader(pathInput), reader.resourceCreator, null)
                val mutatingTree = path.buildMutating(originalTree) { pathTag ->
                    //TODO: Warning when tag type doesn't match the operation
                    when(dataObjectSource.kind) {
                        DataObjectDecoding.DataObjectSourceKind.PATH_SET_MUTATION -> originalTree.root
                        DataObjectDecoding.DataObjectSourceKind.PATH_APPEND_MUTATION -> ListTag().apply { add(originalTree.root) }
                        DataObjectDecoding.DataObjectSourceKind.PATH_MERGE_MUTATION -> {
                            mergePathIntoTag(pathTag, originalTree.root)
                            originalTree.root // Keep instance the same, so all the other data in the StringRangeTree doesn't have to change
                        }
                        else -> throw IllegalArgumentException("Unknown path source kind ${dataObjectSource.kind}")
                    }
                }

                val dataObjectDecoding = DataObjectDecoding.getForReader(reader)
                val decoder = dataObjectDecoding.getDecoderForSource(dataObjectSource, context, reader)

                TreeOperations.forNbt(
                    mutatingTree,
                    reader
                ).withDiagnosticSeverity(DiagnosticSeverity.Warning)
                    .withBranchBehaviorProvider(dataObjectSource.getNBTBranchBehavior(originalTree.root))
                    .analyzeFull(result, decoder)
            }
        }
    }

    fun mergePathIntoTag(pathTag: Tag, newTag: Tag) {
        if(pathTag !is CompoundTag || newTag !is CompoundTag)
            return
        for((pathKey, pathValue) in pathTag.entrySet()) {
            val newValue = newTag[pathKey]
            if(newValue == null)
                newTag.put(pathKey, pathValue)
            else
                mergePathIntoTag(pathValue, newValue)
        }
    }
}