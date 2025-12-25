package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NbtTagArgument.class)
public class NbtTagArgumentMixin implements AnalyzingCommandNode {
    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var nbtReader = TagParser.create(NbtOps.INSTANCE);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        Tag nbt;
        try {
            nbt = nbtReader.parseAsArgument(reader);
        } catch(CommandSyntaxException e) {
            nbt = EndTag.INSTANCE;
            treeBuilder.addNode(nbt, range, range.getStart());
        }
        var tree = treeBuilder.build(nbt);
        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                reader
        ).analyzeFull(result, true, null);
    }
}
