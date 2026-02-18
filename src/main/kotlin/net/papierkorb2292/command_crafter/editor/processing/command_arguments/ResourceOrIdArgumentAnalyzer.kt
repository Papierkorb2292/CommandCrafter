package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceOrIdArgument
import net.minecraft.nbt.EndTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryFileCodec
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer.analyzeForId
import net.papierkorb2292.command_crafter.editor.processing.NbtSuggestionResolver
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree.PartialBuilder
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree.TreeOperations
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs.StringRangeTreeBranchingArgument
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs.allowMalformed
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs.analyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs.furthestAnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs.nbtStringRangeTreeBuilder
import net.papierkorb2292.command_crafter.mixin.editor.processing.ResourceOrIdArgumentAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class ResourceOrIdArgumentAnalyzer : CommandArgumentAnalyzerService<ResourceOrIdArgument<*>> {
    override val argumentTypes: List<Class<out ResourceOrIdArgument<*>>>
        get() = listOf(ResourceOrIdArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: ResourceOrIdArgument<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        val grammar = (type as ResourceOrIdArgumentAccessor).grammar
        val codec = (type as ResourceOrIdArgumentAccessor).codec
        val registryKey = (type as ResourceOrIdArgumentAccessor).registryKey

        val treeBuilder = StringRangeTree.Builder<Tag>()
        val partialBuilder = PartialBuilder<Tag>()

        val parsed = try {
            nbtStringRangeTreeBuilder.set(StringRangeTreeBranchingArgument<Tag>(partialBuilder))
            allowMalformed.set(true)
            analyzingResult.set(PackratParserAdditionalArgs.AnalyzingResultBranchingArgument(result.copyInput()))
            val parsed = grammar.parseForCommands(reader)
            PackratParserAdditionalArgs.popAnalyzingResult(result, null)
            parsed
        } catch(_: CommandSyntaxException) {
            val node = partialBuilder.pushNode()
            node.node = EndTag.INSTANCE
            node.nodeAllowedStart = range.start
            node.startCursor = range.start
            node.endCursor = range.end
            ResourceOrIdArgument.InlineResult<Any, Tag>(EndTag.INSTANCE)
        } finally {
            nbtStringRangeTreeBuilder.remove()
            allowMalformed.remove()
            analyzingResult.remove()
            furthestAnalyzingResult.remove()
        }

        val treeRoot: Tag

        when(parsed) {
            is ResourceOrIdArgument.ReferenceResult<*, *> -> {
                // Analyze up until the next space instead of just analyzing the given range,
                // because otherwise it can analyze the entire rest of the line when invoked through tryAnalyzeNextNode,
                // which is especially problematic for macros, where there might be more nodes later in the line
                var argumentEndCursor = range.start
                while(argumentEndCursor < reader.string.length && reader.string[argumentEndCursor] != ' '
                ) argumentEndCursor++

                val argumentRange = StringRange(range.start, argumentEndCursor)

                analyzeForId(
                    parsed.key.identifier(),
                    PackContentFileType.getOrCreateTypeForDynamicRegistry(registryKey),
                    argumentRange,
                    result,
                    reader
                )

                treeRoot = StringTag.valueOf(parsed.key.identifier().toString())
                treeBuilder.addNode(treeRoot, argumentRange, argumentRange.start)
            }

            is ResourceOrIdArgument.InlineResult -> {
                treeRoot = parsed.value
                partialBuilder.addToBasicBuilder(treeBuilder)
            }
        }

        val isInline = parsed is ResourceOrIdArgument.InlineResult

        val inlineOrReferenceCodec = RegistryFileCodec.create(registryKey, codec)

        val tree = treeBuilder.build(treeRoot)
        var treeOperations = TreeOperations.forNbt(
            tree,
            reader
        ).withSuggestionResolver(NbtSuggestionResolver(reader) { nbtString: StringTag ->
                Identifier.tryParse(nbtString.value()) == null
            })
            .withRegistry(buildContext)
        if(!isInline)
            treeOperations = treeOperations.withDiagnosticSeverity(null)
        treeOperations.analyzeFull(result, inlineOrReferenceCodec)
    }
}