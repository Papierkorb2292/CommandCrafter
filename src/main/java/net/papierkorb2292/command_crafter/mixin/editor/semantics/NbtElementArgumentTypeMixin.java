package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.NbtElementArgumentType;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticTokensCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NbtElementArgumentType.class)
public class NbtElementArgumentTypeMixin implements SemanticCommandNode {
    @Override
    public void command_crafter$createSemanticTokens(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull SemanticTokensBuilder tokens) throws CommandSyntaxException {
        var nbtReader = new StringNbtReader(new StringReader(range.get(context.getInput())));
        ((SemanticTokensCreator)nbtReader).command_crafter$setSemanticTokensBuilder(tokens, reader.getReadCharacters() + range.getStart());
        nbtReader.parseElement();
    }
}
