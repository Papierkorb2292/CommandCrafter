package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.NbtTagArgument
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class NbtTagArgumentAnalyzer : CommandArgumentAnalyzerService<NbtTagArgument> {
    override val argumentTypes
        get() = listOf(NbtTagArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: NbtTagArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        val nbtReader = TagParser.create(NbtOps.INSTANCE)
        (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        val treeBuilder = StringRangeTree.Builder<Tag>()
        @Suppress("UNCHECKED_CAST")
        (nbtReader as StringRangeTreeCreator<Tag>).`command_crafter$setStringRangeTreeBuilder`(treeBuilder)
        val nbt = nbtReader.parseAsArgument(reader)
        val tree: StringRangeTree<Tag> = treeBuilder.build(nbt)
        StringRangeTree.TreeOperations.forNbt(
            tree,
            reader
        ).analyzeFull(result, true, null)
    }
}