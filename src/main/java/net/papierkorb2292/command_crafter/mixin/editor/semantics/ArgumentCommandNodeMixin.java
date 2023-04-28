package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticCommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ArgumentCommandNode.class)
public class ArgumentCommandNodeMixin<T> implements SemanticCommandNode {

    @Shadow(remap = false) @Final private ArgumentType<T> type;

    @Override
    public void command_crafter$createSemanticTokens(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull SemanticTokensBuilder tokens, @NotNull String name) throws CommandSyntaxException {
        if(type instanceof SemanticCommandNode semanticCommandNode) {
            semanticCommandNode.command_crafter$createSemanticTokens(context, range, reader, tokens, name);
            return;
        }
        tokens.addAbsoluteMultiline(range.getStart() + reader.getReadCharacters(), range.getLength(), TokenType.Companion.getPARAMETER(), 0);
    }
}
