package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.NbtElementArgumentType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NbtElementArgumentType.class)
public class NbtElementArgumentTypeMixin implements AnalyzingCommandNode, CustomCompletionsCommandNode {
    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        if(reader.getResourceCreator().getLanguageServer() == null)
            return;
        var readerCopy = reader.copy();
        readerCopy.setCursor(range.getStart());
        var nbtReader = new StringNbtReader(readerCopy);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        NbtElement nbt;
        try {
            nbt = nbtReader.parseElement();
        } catch(CommandSyntaxException e) {
            nbt = NbtEnd.INSTANCE;
            treeBuilder.addNode(nbt, range, range.getStart());
        }
        var tree = treeBuilder.build(nbt);
        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                readerCopy
        ).analyzeFull(result, reader.getResourceCreator().getLanguageServer(), true, null);
    }

    @Override
    public boolean command_crafter$hasCustomCompletions(@NotNull CommandContext<CommandSource> context, @NotNull String name) {
        return true;
    }
}
